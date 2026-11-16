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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

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
    public boolean bool(SettingKey key) {
        return (Boolean) raw(key);
    }

    @Transactional(readOnly = true)
    public int integer(SettingKey key) {
        return ((Number) raw(key)).intValue();
    }

    @Transactional(readOnly = true)
    public Long longOrNull(SettingKey key) {
        Object v = raw(key);
        return v == null ? null : ((Number) v).longValue();
    }

    /** The invoice's bank accounts: bankName, accountName, accountNumber and qr (a data URI, or ""). */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> bankAccounts() {
        return (List<Map<String, Object>>) raw(SettingKey.INVOICE_BANK_ACCOUNTS);
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
            AppSetting row = repo.findById(k.key()).orElse(null);
            Object old = row != null ? row.value : raw(k);
            if (same(old, v)) {
                return;
            }
            if (v == null) { // jsonb value is NOT NULL, so "none" is the absence of a row
                repo.delete(row);
            } else {
                if (row == null) {
                    row = new AppSetting();
                    row.key = k.key();
                }
                row.value = v;
                row.updatedAt = Instant.now();
                row.updatedBy = actor.staff().map(StaffPrincipal::userId).orElse(null);
                repo.save(row);
            }
            audit.record(AuditAction.SETTING_CHANGED, "SETTING", null, "Setting " + k.key() + " changed",
                    AuditChanges.diff().field(k.key(), withoutImages(old), withoutImages(v)));
        });
    }

    /** The activity log says a QR was set, not the whole image. */
    @SuppressWarnings("unchecked")
    private static Object withoutImages(Object value) {
        if (!(value instanceof List<?> accounts)) {
            return value;
        }
        return accounts.stream().map(a -> {
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) a);
            copy.computeIfPresent("qr", (k, qr) -> "".equals(qr) ? "" : "(image)");
            return copy;
        }).toList();
    }

    // jsonb numbers come back as Integer/Double/BigDecimal, so compare numerically.
    private static boolean same(Object old, Object v) {
        return old instanceof Number n && v instanceof BigDecimal b
                ? new BigDecimal(n.toString()).compareTo(b) == 0 : Objects.equals(v, old);
    }

    private Object raw(SettingKey key) {
        return repo.findById(key.key()).map(r -> r.value).orElseGet(() -> {
            if (!key.hasDefault()) {
                throw new NoSuchElementException(key.key());
            }
            return key.defaultValue();
        });
    }
}
