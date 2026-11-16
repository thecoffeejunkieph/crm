package ph.thecoffeejunkie.crm.followups;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** One automatic follow-up rule. Implementations only read; {@link AutoFollowUps} creates and closes the rows. */
public interface AutoRule {

    FollowUpRule rule();

    List<Candidate> find(LocalDate today, RuleSettings s);

    /** Open auto follow-up ids of this rule that should close now, each with the reason. The safety-net pass. */
    Map<Long, String> toClose(LocalDate today, RuleSettings s);
}
