package ph.thecoffeejunkie.crm.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.MailIntegrationTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Transactional: the shared database is emptied inside the test transaction, which rolls back. */
@MailIntegrationTest
@Transactional
class BootstrapAdminIT {

    @Autowired BootstrapAdmin bootstrap;
    @Autowired JdbcTemplate jdbc;

    private void emptyUsers() {
        jdbc.update("delete from one_time_token");
        jdbc.update("update app_setting set updated_by = null");
        jdbc.update("delete from user_warehouse");
        jdbc.update("delete from recovery_code");
        jdbc.update("delete from app_user");
    }

    @Test
    void emptyTableCreatesOneAdminWithPendingInvite() {
        emptyUsers();
        bootstrap.bootstrap("first@boot.it");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select email, role, password_hash, invite_email_status from app_user");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("email", "first@boot.it").containsEntry("role", "ADMIN")
                .containsEntry("password_hash", null).containsEntry("invite_email_status", "PENDING");
        assertThat(jdbc.queryForObject("select count(*) from one_time_token where purpose = 'INVITE'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void nonEmptyTableCreatesNothing() {
        emptyUsers();
        jdbc.update("insert into app_user (email, first_name, last_name, role) values "
                + "('existing@boot.it', 'E', 'X', 'SALES')");
        bootstrap.bootstrap("second@boot.it");
        assertThat(jdbc.queryForList("select email from app_user", String.class)).containsExactly("existing@boot.it");
    }

    @Test
    void blankPropertyDoesNothing() {
        emptyUsers();
        bootstrap.bootstrap("");
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isZero();
    }
}
