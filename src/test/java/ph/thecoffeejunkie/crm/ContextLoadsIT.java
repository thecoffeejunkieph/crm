package ph.thecoffeejunkie.crm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class ContextLoadsIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoadsAndAppRoleIsNotOwner() {
        assertEquals("crm_app", jdbc.queryForObject("select current_user", String.class));
    }
}
