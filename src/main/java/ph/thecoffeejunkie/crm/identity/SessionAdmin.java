package ph.thecoffeejunkie.crm.identity;

/** Kills sessions. Principal names are {@code user:<id>} / {@code contact:<id>}. */
public interface SessionAdmin {

    void revokeAll(String principalName);

    void revokeAllExcept(String principalName, String keepSessionId);

    /** Kills one session by its real id (never client-supplied; resolve hashes server-side first). */
    void revoke(String sessionId);
}
