package ph.thecoffeejunkie.crm.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AuditEventResponse(long id, Instant at, String actorType, Long actorId, String actorName,
                                 String action, String entityType, Long entityId, String summary,
                                 Map<String, List<Object>> changes) {
}
