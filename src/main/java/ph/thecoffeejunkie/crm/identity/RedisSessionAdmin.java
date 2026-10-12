package ph.thecoffeejunkie.crm.identity;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@Component
class RedisSessionAdmin implements SessionAdmin {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    RedisSessionAdmin(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
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
    public void revoke(String sessionId) {
        sessions.deleteById(sessionId);
    }
}
