package ph.thecoffeejunkie.crm.audit;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Field-level diff: name to [old, new]; unchanged fields are skipped. */
public final class AuditChanges {

    private final Map<String, List<Object>> fields = new LinkedHashMap<>();

    private AuditChanges() {
    }

    public static AuditChanges diff() {
        return new AuditChanges();
    }

    public AuditChanges field(String name, Object oldValue, Object newValue) {
        boolean same = oldValue instanceof BigDecimal a && newValue instanceof BigDecimal b
                ? a.compareTo(b) == 0 : Objects.equals(oldValue, newValue);
        if (!same) {
            fields.put(name, Arrays.asList(oldValue, newValue)); // asList: tolerates null values
        }
        return this;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public Map<String, List<Object>> toMap() {
        return new LinkedHashMap<>(fields);
    }
}
