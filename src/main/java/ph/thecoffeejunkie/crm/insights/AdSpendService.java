package ph.thecoffeejunkie.crm.insights;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monthly ad spend per lead source (spec 2026-11-22). Plain SQL: the source list belongs to the customers module, so
 * it is read as a table, not through its beans. Last write wins.
 */
@Service
class AdSpendService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final String ROW = "select s.id, s.name, s.archived_at is not null as archived, a.amount "
            + "from lead_source s left join ad_spend a on a.source_id = s.id and a.month = :month ";

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditLog audit;
    private final CurrentActor actor;

    AdSpendService(NamedParameterJdbcTemplate jdbc, AuditLog audit, CurrentActor actor) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.actor = actor;
    }

    /** Active sources, plus archived ones that have spend that month. */
    @Transactional(readOnly = true)
    List<AdSpendRow> list(YearMonth m) {
        return jdbc.query(ROW + "where s.archived_at is null or a.id is not null order by lower(s.name)",
                Map.of("month", m.atDay(1)), (rs, i) -> new AdSpendRow(rs.getLong(1), rs.getString(2), rs.getBoolean(3),
                        rs.getBigDecimal(4)));
    }

    @Transactional
    AdSpendRow set(long sourceId, YearMonth m, AdSpendRequest req) {
        String name = sourceName(sourceId);
        jdbc.update("insert into ad_spend (source_id, month, amount, updated_by) values (:s, :month, :amount, :by) "
                + "on conflict (source_id, month) do update set amount = excluded.amount, updated_by = excluded.updated_by, "
                + "updated_at = now(), version = ad_spend.version + 1",
                Map.of("s", sourceId, "month", m.atDay(1), "amount", req.amount(), "by", actor.scope().userId()));
        AdSpendRow row = jdbc.queryForObject(ROW + "where s.id = :s", Map.of("s", sourceId, "month", m.atDay(1)),
                (rs, i) -> new AdSpendRow(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getBigDecimal(4)));
        audit.record(AuditAction.AD_SPEND_SET, "AD_SPEND", sourceId, "Set " + name + " ad spend for " + m.format(MONTH)
                + " to " + row.amount().toPlainString(), null);
        return row;
    }

    @Transactional
    void clear(long sourceId, YearMonth m) {
        int removed = jdbc.update("delete from ad_spend where source_id = :s and month = :month",
                Map.of("s", sourceId, "month", m.atDay(1)));
        if (removed > 0) {
            audit.record(AuditAction.AD_SPEND_SET, "AD_SPEND", sourceId, "Cleared " + sourceName(sourceId)
                    + " ad spend for " + m.format(MONTH), null);
        }
    }

    private String sourceName(long sourceId) {
        return jdbc.query("select name from lead_source where id = :s", Map.of("s", sourceId), (rs, i) -> rs.getString(1))
                .stream().findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Lead source not found."));
    }
}
