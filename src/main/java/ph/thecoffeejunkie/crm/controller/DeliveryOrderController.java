package ph.thecoffeejunkie.crm.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.constant.DeliveryOrderStatus;
import java.util.List;
import ph.thecoffeejunkie.crm.dto.request.DeliveryOrderUpdateRequest;
import ph.thecoffeejunkie.crm.dto.response.DeliveryOrderResponse;
import ph.thecoffeejunkie.crm.dto.response.PageResponse;
import ph.thecoffeejunkie.crm.service.DeliveryOrderPdfService;
import ph.thecoffeejunkie.crm.service.DeliveryOrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/delivery-orders")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;
    private final DeliveryOrderPdfService deliveryOrderPdfService;

    @GetMapping
    public PageResponse<DeliveryOrderResponse> getAll(@RequestParam(defaultValue = "0") int pageNumber,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(defaultValue = "deliveryOrderNumber") String sortBy,
                                                        @RequestParam(defaultValue = "DESC") String sortDirection,
                                                        @RequestParam(required = false) DeliveryOrderStatus status) {
        return deliveryOrderService.findAll(status, PageRequest.of(pageNumber - 1,
                pageSize, Sort.Direction.valueOf(sortDirection.toUpperCase()), sortBy));
    }

    @GetMapping("/{id}")
    public DeliveryOrderResponse getById(@PathVariable Long id) {
        return deliveryOrderService.findById(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable Long id) {
        DeliveryOrderPdfService.DeliveryOrderPdf pdf = deliveryOrderPdfService.generate(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdf.fileName() + "\"")
                .body(pdf.content());
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @PatchMapping("/{id}")
    public DeliveryOrderResponse update(@PathVariable Long id, @RequestBody @Valid DeliveryOrderUpdateRequest request) {
        return deliveryOrderService.update(id, request);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @PostMapping("/{id}/ready-for-pickup")
    public DeliveryOrderResponse markReadyForPickup(@PathVariable Long id) {
        return deliveryOrderService.markReadyForPickup(id);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @PostMapping(value = "/{id}/picked-up", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DeliveryOrderResponse markPickedUp(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files) {
        return deliveryOrderService.markPickedUp(id, files);
    }

    @PreAuthorize("hasAnyAuthority('ADMIN','WAREHOUSE')")
    @PostMapping(value = "/{id}/delivered", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DeliveryOrderResponse markDelivered(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files) {
        return deliveryOrderService.markDelivered(id, files);
    }
}
