package ph.thecoffeejunkie.crm.leads;

import java.util.Set;

public enum LeadStatus {
    NEW, CONTACTED, QUALIFIED, LOST, CONVERTED;

    static final Set<LeadStatus> OPEN = Set.of(NEW, CONTACTED, QUALIFIED);
}
