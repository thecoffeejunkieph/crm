package ph.thecoffeejunkie.crm.customers;

import jakarta.validation.Validator;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.Scope;
import ph.thecoffeejunkie.crm.shared.Sheets;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ph.thecoffeejunkie.crm.shared.Sheets.decimal;
import static ph.thecoffeejunkie.crm.shared.Sheets.enumOf;

/** Customer CSV import and CSV/XLSX export (F-110). Import columns are the export's; extra columns are ignored. */
@Service
class CustomerSheets {

    static final List<String> COLUMNS = List.of("type", "displayName", "businessName", "tin", "address", "phone",
            "email", "creditLimit", "paymentTerms", "taxTreatment", "notes", "socialMedia");

    private final Customers customers;
    private final CustomerRepository repo;
    private final Users users;
    private final AuditLog audit;
    private final Validator validator;

    CustomerSheets(Customers customers, CustomerRepository repo, Users users, AuditLog audit, Validator validator) {
        this.customers = customers;
        this.repo = repo;
        this.users = users;
        this.audit = audit;
        this.validator = validator;
    }

    /** Active customers the caller can see. */
    @Transactional
    ResponseEntity<byte[]> export(Scope s, String format) {
        // ponytail: loads every visible customer in memory; stream if the list grows past tens of thousands.
        List<Customer> all = repo.findAll(CustomerScopes.visibleTo(s), Sort.by("displayName"));
        var repIds = new HashSet<>(all.stream().map(c -> c.assignedRepId).filter(Objects::nonNull).toList());
        Map<Long, String> reps = repIds.isEmpty() ? Map.of() : users.displayNames(repIds);
        List<List<?>> rows = all.stream().<List<?>>map(c -> java.util.Arrays.asList(c.id, c.type, c.displayName,
                c.businessName, c.tin, c.address, c.phone, c.email, c.creditLimit, c.paymentTerms, c.taxTreatment,
                c.notes, c.socialMedia, c.assignedRepId == null ? null : reps.get(c.assignedRepId))).toList();
        var header = new java.util.ArrayList<>(List.of("id"));
        header.addAll(COLUMNS);
        header.add("assignedRep");
        audit.record(AuditAction.EXPORTED, "CUSTOMER", null, "Exported " + rows.size() + " customers", null);
        return Sheets.export("customers", format, header, rows);
    }

    /** Creates new customers only; nothing is written on a dry run or when any row fails. */
    @Transactional
    Sheets.ImportResult importCsv(Scope s, byte[] file, boolean dryRun) {
        return Sheets.importCsv(file, dryRun, validator, CustomerSheets::request, r -> null,
                r -> customers.create(s, r));
    }

    static CustomerRequest request(Map<String, String> r) {
        return new CustomerRequest(enumOf(CustomerType.class, "type", r.get("type")), r.get("displayname"),
                r.get("businessname"), r.get("tin"), r.get("address"), r.get("phone"), r.get("email"),
                decimal("creditLimit", r.get("creditlimit")),
                enumOf(PaymentTerms.class, "paymentTerms", r.getOrDefault("paymentterms", "DUE_ON_RECEIPT")),
                null, r.get("notes"), null, enumOf(TaxTreatment.class, "taxTreatment", r.get("taxtreatment")),
                r.get("socialmedia"));
    }
}
