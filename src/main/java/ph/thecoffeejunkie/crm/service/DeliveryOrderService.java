package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.constant.DeliveryOrderStatus;
import ph.thecoffeejunkie.crm.constant.NotificationType;
import ph.thecoffeejunkie.crm.dto.request.DeliveryOrderUpdateRequest;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.entity.DeliveryOrder;
import ph.thecoffeejunkie.crm.entity.Invoice;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.repository.DeliveryOrderRepository;
import ph.thecoffeejunkie.crm.util.CustomMapper;
import ph.thecoffeejunkie.crm.util.DeliveryOrderNumberGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final DeliveryOrderRepository repository;
    private final InventoryService inventoryService;
    private final DeliveryOrderNumberGenerator numberGenerator;
    private final StorageService storageService;
    private final NotificationService notificationService;

    public DeliveryOrderResponse createForInvoice(Invoice invoice) {
        log.info("Creating delivery order for invoice {}...", invoice.getInvoiceNumber());

        DeliveryOrder order = new DeliveryOrder();
        order.setDeliveryOrderNumber(numberGenerator.generate());
        order.setInvoice(invoice);
        order.setStatus(DeliveryOrderStatus.PENDING);
        order.setDeliveryAddress(invoice.getCustomer() != null ? invoice.getCustomer().getAddress() : null);
        DeliveryOrderResponse response = CustomMapper.toDeliveryOrderResponse(repository.save(order));

        log.info("Created delivery order {} for invoice {}", response.id(), invoice.getInvoiceNumber());
        return response;
    }

    public DeliveryOrderResponse findById(Long id) {
        log.info("Getting delivery order with id: {}", id);

        return repository.findById(id)
                .map(CustomMapper::toDeliveryOrderResponse)
                .orElseThrow(() -> {
                    log.warn("Delivery order not found with id: {}", id);
                    return ResourceNotFoundException.of("Delivery order", id);
                });
    }

    public PageResponse<DeliveryOrderResponse> findAll(DeliveryOrderStatus status, PageRequest pageRequest) {
        log.info("Getting all delivery orders...");

        Page<DeliveryOrder> page = status != null
                ? repository.findByStatus(status, pageRequest)
                : repository.findAll(pageRequest);

        log.info("Found {} delivery orders", page.getTotalElements());
        return new PageResponse<>(
                page.getPageable().getPageNumber() + 1,
                page.getPageable().getPageSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getContent().stream().map(CustomMapper::toDeliveryOrderResponse).toList()
        );
    }

    public DeliveryOrderResponse update(Long id, DeliveryOrderUpdateRequest request) {
        log.info("Updating delivery order with id: {}", id);

        DeliveryOrder order = getOrThrow(id);

        if (order.getStatus() == DeliveryOrderStatus.DELIVERED) {
            throw new InvalidRequestException("Cannot edit a delivery order that has already been delivered");
        }

        order.setDeliveryInstructions(request.deliveryInstructions());
        order.setDeliveryAddress(request.deliveryAddress());
        order.setTargetDeliveryDate(request.targetDeliveryDate());
        DeliveryOrderResponse response = CustomMapper.toDeliveryOrderResponse(repository.save(order));

        log.info("Updated delivery order {}", id);
        return response;
    }

    public DeliveryOrderResponse markReadyForPickup(Long id) {
        log.info("Marking delivery order as ready for pickup with id: {}", id);

        DeliveryOrder order = getOrThrow(id);

        if (order.getStatus() != DeliveryOrderStatus.PENDING) {
            throw new InvalidRequestException("Only pending delivery orders can be marked ready for pickup");
        }

        order.setStatus(DeliveryOrderStatus.READY_FOR_PICKUP);
        DeliveryOrderResponse response = CustomMapper.toDeliveryOrderResponse(repository.save(order));

        log.info("Marked delivery order {} as ready for pickup", id);
        return response;
    }

    public DeliveryOrderResponse markPickedUp(Long id, List<MultipartFile> files) {
        log.info("Marking delivery order as picked up with id: {}", id);

        DeliveryOrder order = getOrThrow(id);

        if (order.getStatus() != DeliveryOrderStatus.READY_FOR_PICKUP) {
            throw new InvalidRequestException("Only delivery orders that are ready for pickup can be marked picked up");
        }

        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new InvalidRequestException("At least one proof of pickup file is required");
        }

        // Store (and validate) the proof first, so a rejected file can't leave stock already deducted.
        List<String> keys = writeProofFiles(id, "proof-of-pickup", files);

        inventoryService.deliverForInvoice(order.getInvoice());

        order.setProofOfPickupPaths(keys);
        order.setPickedUpAt(LocalDateTime.now());
        order.setStatus(DeliveryOrderStatus.PICKED_UP);
        DeliveryOrderResponse response = CustomMapper.toDeliveryOrderResponse(repository.save(order));

        log.info("Marked delivery order {} as picked up", id);
        return response;
    }

    public DeliveryOrderResponse markDelivered(Long id, List<MultipartFile> files) {
        log.info("Marking delivery order as delivered with id: {}", id);

        DeliveryOrder order = getOrThrow(id);

        if (order.getStatus() != DeliveryOrderStatus.PICKED_UP) {
            throw new InvalidRequestException("Only delivery orders that have been picked up can be marked delivered");
        }

        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new InvalidRequestException("At least one proof of delivery file is required");
        }

        order.setProofOfDeliveryPaths(writeProofFiles(id, "proof-of-delivery", files));
        order.setDeliveredAt(LocalDateTime.now());
        order.setStatus(DeliveryOrderStatus.DELIVERED);
        DeliveryOrderResponse response = CustomMapper.toDeliveryOrderResponse(repository.save(order));

        log.info("Marked delivery order {} as delivered", id);
        Invoice invoice = order.getInvoice();
        notificationService.toOwner(invoice.getSalesRep(), NotificationType.DELIVERY_ORDER_DELIVERED, order.getId(),
                "Delivered: " + order.getDeliveryOrderNumber(),
                "Invoice " + invoice.getInvoiceNumber() + " for "
                        + NotificationService.customerName(invoice.getCustomer()) + " was delivered.");
        return response;
    }

    private DeliveryOrder getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delivery order not found with id: {}", id);
                    return ResourceNotFoundException.of("Delivery order", id);
                });
    }

    private List<String> writeProofFiles(Long deliveryOrderId, String proofType, List<MultipartFile> files) {
        String prefix = "delivery-orders/" + deliveryOrderId + "/" + proofType;
        List<String> keys = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            keys.add(storageService.store(prefix, file, StorageService.PROOF_TYPES));
        }
        return keys;
    }
}
