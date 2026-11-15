package ph.thecoffeejunkie.crm.sales;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.PageResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
class ReportController {

    private final SalesReports reports;
    private final CurrentActor actor;

    ReportController(SalesReports reports, CurrentActor actor) {
        this.reports = reports;
        this.actor = actor;
    }

    @GetMapping("/api/reports/missing-2307")
    @PreAuthorize("@salesPermissions.canRead()")
    PageResponse<Missing2307Row> missing2307(@RequestParam(required = false) Long customerId,
                                             @RequestParam(required = false) Long repId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             PageQuery page) {
        return reports.missing2307(actor.scope(), customerId, repId, from, to, page);
    }

    @GetMapping("/api/reports/missing-2307.csv")
    @PreAuthorize("@salesPermissions.canRead()")
    ResponseEntity<byte[]> missing2307Csv(@RequestParam(required = false) Long customerId,
                                          @RequestParam(required = false) Long repId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String csv = reports.missing2307Csv(actor.scope(), customerId, repId, from, to);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("missing-2307.csv").build().toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
