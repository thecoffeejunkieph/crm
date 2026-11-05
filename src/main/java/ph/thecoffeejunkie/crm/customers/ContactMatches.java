package ph.thecoffeejunkie.crm.customers;

import ph.thecoffeejunkie.crm.shared.Scope;

import java.util.List;
import java.util.Objects;

/** Records sharing an email or phone: the ones the caller may open, and how many they may not. */
public record ContactMatches(List<Match> visible, int hidden) {

    public static final int LIMIT = 5;

    public record Match(long id, String name) {
    }

    /** A matching record and the rep it belongs to. */
    public record Candidate(long id, String name, Long repId) {
    }

    /** Admin sees all; a rep sees only what is assigned to them. The rest is only counted. */
    public static ContactMatches split(List<Candidate> all, Scope s) {
        List<Candidate> mine = all.stream().filter(c -> s.isAdmin() || Objects.equals(c.repId(), s.userId())).toList();
        return new ContactMatches(mine.stream().limit(LIMIT).map(c -> new Match(c.id(), c.name())).toList(),
                all.size() - mine.size());
    }
}
