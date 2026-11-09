package ph.thecoffeejunkie.crm.catalog;

import jakarta.validation.Validator;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.Sheets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static ph.thecoffeejunkie.crm.shared.Sheets.decimal;
import static ph.thecoffeejunkie.crm.shared.Sheets.enumOf;

/** Product CSV import (admin) and CSV/XLSX export (F-110). Cost is exported for admins only. */
@Service
class ProductSheets {

    private final Catalog catalog;
    private final ProductRepository repo;
    private final AuditLog audit;
    private final CurrentActor actor;
    private final Validator validator;

    ProductSheets(Catalog catalog, ProductRepository repo, AuditLog audit, CurrentActor actor, Validator validator) {
        this.catalog = catalog;
        this.repo = repo;
        this.audit = audit;
        this.actor = actor;
        this.validator = validator;
    }

    @Transactional
    ResponseEntity<byte[]> export(String format) {
        boolean admin = actor.staff().filter(s -> s.role() == Role.ADMIN).isPresent();
        List<Product> all = repo.findAll((Specification<Product>) (r, q, cb) -> cb.isNull(r.get("archivedAt")),
                Sort.by("name"));
        List<List<?>> rows = all.stream().<List<?>>map(p -> {
            List<Object> row = new ArrayList<>(Arrays.asList(p.id, p.sku, p.name, p.description, p.unit, p.price));
            if (admin) {
                row.add(p.cost);
            }
            row.add(p.taxClass);
            return row;
        }).toList();
        List<String> header = new ArrayList<>(List.of("id", "sku", "name", "description", "unit", "price"));
        if (admin) {
            header.add("cost");
        }
        header.add("taxClass");
        audit.record(AuditAction.EXPORTED, "PRODUCT", null, "Exported " + rows.size() + " products", null);
        return Sheets.export("products", format, header, rows);
    }

    /** New products only: a SKU already in the catalog, or twice in the file, fails its row. */
    @Transactional
    Sheets.ImportResult importCsv(byte[] file, boolean dryRun) {
        Set<String> taken = repo.activeSkus();
        return Sheets.importCsv(file, dryRun, validator, ProductSheets::request,
                r -> taken.add(r.sku().trim().toLowerCase(Locale.ROOT)) ? null : "sku " + r.sku().trim() + " already exists",
                catalog::create);
    }

    static ProductRequest request(Map<String, String> r) {
        return new ProductRequest(r.get("sku"), r.get("name"), r.get("description"),
                enumOf(ProductUnit.class, "unit", r.get("unit")), decimal("price", r.get("price")),
                decimal("cost", r.get("cost")),
                enumOf(TaxClass.class, "taxClass", r.getOrDefault("taxclass", "VATABLE")), null);
    }
}
