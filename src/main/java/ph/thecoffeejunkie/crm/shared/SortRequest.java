package ph.thecoffeejunkie.crm.shared;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A validated list sort. Callers do {@code repo.findAll(spec.and(sort.orderSpec()), sort.pageable())}.
 * Own fields sort through the Pageable; a linked-name key sorts by a correlated subquery added by the spec.
 * Nulls last and {@code id} as the final tie-breaker in both cases; the count query never carries an order.
 */
public record SortRequest<T>(Pageable pageable, Specification<T> orderSpec) {

    /**
     * Sorts by {@code (select displayColumn from table x where x.id = root.<idAttrOnRoot>)}; idAttrOnRoot is the entity attribute (e.g. customerId).
     * {@code displayColumn} is a SQL expression over the unqualified columns of {@code table} (a fixed literal, never user input).
     */
    public record SortJoin<T>(String table, String idAttrOnRoot, String displayColumn) {
    }

    /**
     * Sorts by a constant SQL template; each {@code ?} is filled, in order, by the root attribute named in {@code attrs}.
     * Build only in {@code static final} fields: the template is spliced into SQL, never user input.
     */
    public record SortExpr(String sqlTemplate, Class<?> type, List<String> attrs) {
    }

    /** Sorts by one root attribute under a different key, for a frontend column key that is not the attribute name. */
    public static SortExpr alias(String attr, Class<?> type) {
        return new SortExpr("?", type, List.of(attr));
    }

    /**
     * For JDBC lists: {@code " order by <column> <dir> nulls last, <tieBreaker>"} for a validated key, else
     * {@code " order by <defaultOrder>"}. The key must be in {@code columns} (SQL fragments, fixed literals) and the
     * direction asc|desc, otherwise 400 like every other list; user input never reaches the SQL text.
     */
    public static String sqlOrderBy(PageQuery page, Map<String, String> columns, String defaultOrder, String tieBreaker) {
        of(page, columns.keySet(), Map.of(), Sort.unsorted()); // validates key and direction
        if (page.sort() == null || page.sort().isBlank()) {
            return " order by " + defaultOrder;
        }
        String[] parts = page.sort().split(",", 2);
        boolean desc = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc");
        return " order by " + columns.get(parts[0].trim()) + (desc ? " desc" : " asc") + " nulls last, " + tieBreaker;
    }

    public static <T> SortRequest<T> of(PageQuery page, Set<String> ownFields, Map<String, SortJoin<T>> joins,
                                        Sort defaultSort) {
        return of(page, ownFields, joins, Map.of(), defaultSort);
    }

    public static <T> SortRequest<T> of(PageQuery page, Set<String> ownFields, Map<String, SortJoin<T>> joins,
                                        Map<String, SortExpr> exprs, Sort defaultSort) {
        int p = page.page() == null ? 1 : Math.max(1, page.page()); // page 0 would make PageRequest throw a 500
        int s = page.size() == null ? 20 : Math.clamp(page.size(), 1, 100);
        String sort = page.sort();
        if (sort == null || sort.isBlank()) {
            return new SortRequest<>(PageRequest.of(p - 1, s, defaultSort.and(Sort.by("id"))), Specification.unrestricted());
        }
        String[] parts = sort.split(",", 2);
        String key = parts[0].trim();
        String dir = parts.length > 1 ? parts[1].trim() : "asc";
        boolean known = ownFields.contains(key) || joins.containsKey(key) || exprs.containsKey(key);
        if (!known || !(dir.equalsIgnoreCase("asc") || dir.equalsIgnoreCase("desc"))) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Cannot sort by '" + sort + "'",
                    Map.of("field", "sort"));
        }
        Sort.Direction d = Sort.Direction.fromString(dir);
        SortJoin<T> join = joins.get(key);
        SortExpr computed = join != null
                ? new SortExpr("(select " + join.displayColumn() + " from " + join.table() + " x where x.id = ?)",
                        String.class, List.of(join.idAttrOnRoot()))
                : exprs.get(key);
        if (computed == null) {
            Sort order = Sort.by(new Sort.Order(d, key).nullsLast()).and(Sort.by("id"));
            return new SortRequest<>(PageRequest.of(p - 1, s, order), Specification.unrestricted());
        }
        Specification<T> order = (root, query, cb) -> {
            if (query.getResultType() != Long.class) { // Long = the count query
                Expression<?>[] args = computed.attrs().stream().map(root::get).toArray(Expression[]::new);
                Expression<?> value = ((HibernateCriteriaBuilder) cb).sql(computed.sqlTemplate(), computed.type(), args);
                Order by = d == Sort.Direction.ASC ? cb.asc(value) : cb.desc(value);
                // Postgres sorts nulls last for ASC already; DESC needs the explicit guard.
                List<Order> orders = d == Sort.Direction.ASC ? List.of(by, cb.asc(root.get("id")))
                        : List.of(cb.asc(cb.selectCase().when(cb.isNull(value), 1).otherwise(0)), by, cb.asc(root.get("id")));
                query.orderBy(orders);
            }
            return null;
        };
        return new SortRequest<>(PageRequest.of(p - 1, s), order);
    }
}
