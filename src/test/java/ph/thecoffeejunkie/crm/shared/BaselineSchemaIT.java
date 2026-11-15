package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs as crm_app, so these tests exercise the grants the app actually gets. Rolled back after each test. */
@IntegrationTest
@Transactional
class BaselineSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void appRoleCanInsertAndReadAudit() {
        String marker = UUID.randomUUID().toString();
        jdbc.update("insert into audit_event (actor_type, action, summary) values ('SYSTEM', 'TEST', ?)", marker);

        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where summary = ?", Integer.class, marker));
    }

    @Test
    void appRoleCannotUpdateAudit() {
        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> jdbc.update("update audit_event set summary = 'x'"));
        assertTrue(ex.getMostSpecificCause().getMessage().contains("permission denied"), ex.getMessage());
    }

    @Test
    void appRoleCannotDeleteAudit() {
        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> jdbc.update("delete from audit_event"));
        assertTrue(ex.getMostSpecificCause().getMessage().contains("permission denied"), ex.getMessage());
    }

    @Test
    void onlyOneDefaultWarehouse() {
        jdbc.update("insert into warehouse (code, name, is_default) values (?, 'A', true)",
                "A-" + UUID.randomUUID());

        assertThrows(DuplicateKeyException.class, () ->
                jdbc.update("insert into warehouse (code, name, is_default) values (?, 'B', true)",
                        "B-" + UUID.randomUUID()));
    }
}
