package ph.thecoffeejunkie.crm.fulfilment;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

/** Role gates are URL rules in SecurityConfig; warehouse and rep scope is checked in the service (404). */
@RestController
class DeliveryOrderController {

    private final DeliveryService deliveries;

    DeliveryOrderController(DeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @GetMapping("/api/delivery-orders")
    PageResponse<DeliveryOrderResponse> list(@RequestParam(required = false) DeliveryStatus status,
                                             @RequestParam(required = false) Long warehouseId, PageQuery page) {
        return deliveries.list(status, warehouseId, page);
    }

    @GetMapping("/api/delivery-orders/{id}")
    DeliveryOrderResponse get(@PathVariable long id) {
        return deliveries.get(id);
    }

    @PostMapping("/api/delivery-orders/{id}/pick")
    DeliveryOrderResponse pick(@PathVariable long id, @Valid @RequestBody PickRequest req) {
        return deliveries.pick(id, req);
    }

    @PostMapping("/api/delivery-orders/{id}/dispatch")
    DeliveryOrderResponse dispatch(@PathVariable long id) {
        return deliveries.dispatch(id);
    }

    /** Both parts are checked in the service, so a missing one is PROOF_REQUIRED rather than a binding 400. */
    @PostMapping(value = "/api/delivery-orders/{id}/deliver", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    DeliveryOrderResponse deliver(@PathVariable long id,
                                  @RequestPart(value = "photo", required = false) MultipartFile photo,
                                  @RequestParam(value = "receiverName", required = false) String receiverName) {
        return deliveries.deliver(id, photo, receiverName);
    }
}
