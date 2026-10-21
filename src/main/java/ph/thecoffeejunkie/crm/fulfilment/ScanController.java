package ph.thecoffeejunkie.crm.fulfilment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.regex.Pattern;

/** Resolves a scanned QR text. Unparseable, unknown and out-of-scope codes are all the same 404. */
@RestController
class ScanController {

    private static final Pattern CODE = Pattern.compile("^crm:(DO|TR|PR|WH):(\\d+)$");

    private final LabelService labels;

    ScanController(LabelService labels) {
        this.labels = labels;
    }

    @GetMapping("/api/scan/{code}")
    ScanResult scan(@PathVariable String code) {
        var m = CODE.matcher(code);
        if (!m.matches() || m.group(2).length() > 18) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Code not recognised");
        }
        LabelKind kind = LabelKind.valueOf(m.group(1));
        long id = Long.parseLong(m.group(2));
        labels.find(kind, id);
        return new ScanResult(kind.type, id);
    }
}
