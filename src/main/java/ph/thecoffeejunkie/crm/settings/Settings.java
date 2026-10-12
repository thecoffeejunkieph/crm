package ph.thecoffeejunkie.crm.settings;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Public facade: other modules read thresholds here; admins change them through the controller. */
@Service
public class Settings {

    private final AppSettingRepository repo;
    private final AuditLog audit;
    private final CurrentActor actor;

    Settings(AppSettingRepository repo, AuditLog audit, CurrentActor actor) {
        this.repo = repo;
        this.audit = audit;
        this.actor = actor;
    }

    @Cacheable(value = "settings", key = "'d:' + #key")
    @Transactional(readOnly = true)
    public BigDecimal decimal(SettingKey key) {
        return new BigDecimal(raw(key).toString());
    }

    @Cacheable(value = "settings", key = "'t:' + #key")
    @Transactional(readOnly = true)
    public String text(SettingKey key) {
        return raw(key).toString();
    }

    @Transactional(readOnly = true)
    Map<String, Object> all() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (SettingKey k : SettingKey.values()) {
            out.put(k.key(), raw(k));
        }
        return out;
    }

    /** Validates every value before writing any. */
    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public void update(Map<SettingKey, JsonNode> values) {
        Map<SettingKey, Object> valid = new LinkedHashMap<>();
        values.forEach((k, v) -> valid.put(k, k.validate(v)));
        valid.forEach((k, v) -> {
            AppSetting row = repo.findById(k.key()).orElseThrow();
            Object old = row.value;
            if (same(old, v)) {
                return;
            }
            row.value = v;
            row.updatedAt = Instant.now();
            row.updatedBy = actor.staff().map(StaffPrincipal::userId).orElse(null);
            audit.record(AuditAction.SETTING_CHANGED, "SETTING", null, "Setting " + k.key() + " changed",
                    AuditChanges.diff().field(k.key(), old, v));
        });
    }

    // jsonb numbers come back as Integer/Double/BigDecimal, so compare numerically.
    private static boolean same(Object old, Object v) {
        return old instanceof Number n && v instanceof BigDecimal b
                ? new BigDecimal(n.toString()).compareTo(b) == 0 : v.equals(old);
    }

    private Object raw(SettingKey key) {
        return repo.findById(key.key()).orElseThrow().value;
    }
}
