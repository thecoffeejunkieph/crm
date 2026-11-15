package ph.thecoffeejunkie.crm.identity;

import java.util.List;

/** Lists and kills sessions. Principal names are {@code user:<id>} / {@code contact:<id>}. */
public interface SessionAdmin {

    /** Sessions of one principal; {@code current} marks {@code currentSessionId} (may be null). */
    List<SessionResponse> list(String principalName, String currentSessionId);

    void revokeAll(String principalName);

    void revokeAllExcept(String principalName, String keepSessionId);

    /** Kills the principal's session whose id hashes to {@code hash}; false if it has none. */
    boolean revokeByHash(String principalName, String hash);
}
