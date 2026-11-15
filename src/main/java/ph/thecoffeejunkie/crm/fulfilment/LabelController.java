package ph.thecoffeejunkie.crm.fulfilment;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Role gate (ADMIN, WAREHOUSE) is a URL rule in SecurityConfig; scope is checked per id in the service. */
@RestController
class LabelController {

    private final LabelService labels;

    LabelController(LabelService labels) {
        this.labels = labels;
    }

    @GetMapping(value = "/api/labels", produces = MediaType.APPLICATION_PDF_VALUE)
    byte[] labels(@RequestParam LabelKind type, @RequestParam List<Long> ids) {
        return labels.render(type, ids);
    }
}
