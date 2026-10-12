package ph.thecoffeejunkie.crm.identity;

import java.time.Instant;

/** {@code id} is the SHA-256 hex of the real session id; the real id is never exposed. */
record SessionResponse(String id, String clientType, String clientIp, String userAgent, Instant createdAt,
                       Instant lastAccessedAt, boolean current) {
}
