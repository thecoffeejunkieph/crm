package ph.thecoffeejunkie.crm;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void modulesHaveNoCyclesOrReachIns() {
        ApplicationModules.of(CrmApplication.class).verify();
    }
}
