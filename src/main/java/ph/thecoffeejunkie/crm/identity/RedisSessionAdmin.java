package ph.thecoffeejunkie.crm.identity;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
class RedisSessionAdmin implements SessionAdmin {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    RedisSessionAdmin(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    @Override
    public List<SessionResponse> list(String principalName, String currentSessionId) {
        return sessions.findByPrincipalName(principalName).entrySet().stream().map(e -> {
            Session s = e.getValue();
            return new SessionResponse(sha256hex(e.getKey()), s.getAttribute("clientType"),
                    s.getAttribute("clientIp"), s.getAttribute("userAgent"), s.getCreationTime(),
                    s.getLastAccessedTime(), e.getKey().equals(currentSessionId));
        }).sorted((a, b) -> b.lastAccessedAt().compareTo(a.lastAccessedAt())).toList();
    }

    @Override
    public void revokeAll(String principalName) {
        revokeAllExcept(principalName, null);
    }

    @Override
    public void revokeAllExcept(String principalName, String keepSessionId) {
        sessions.findByPrincipalName(principalName).keySet().stream()
                .filter(id -> !id.equals(keepSessionId)).forEach(sessions::deleteById);
    }

    @Override
    public boolean revokeByHash(String principalName, String hash) {
        var id = sessions.findByPrincipalName(principalName).keySet().stream()
                .filter(s -> sha256hex(s).equals(hash)).findFirst();
        id.ifPresent(sessions::deleteById);
        return id.isPresent();
    }

    private static String sha256hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
