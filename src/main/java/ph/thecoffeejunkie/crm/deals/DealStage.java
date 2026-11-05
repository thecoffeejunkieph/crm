package ph.thecoffeejunkie.crm.deals;

import java.util.Set;

public enum DealStage {
    DISCOVERY, PROPOSAL, NEGOTIATION, WON, LOST;

    static final Set<DealStage> OPEN = Set.of(DISCOVERY, PROPOSAL, NEGOTIATION);
}
