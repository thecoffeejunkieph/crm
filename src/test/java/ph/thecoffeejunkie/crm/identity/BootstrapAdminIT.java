package ph.thecoffeejunkie.crm.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    @Autowired OneTimeTokens tokens;
    @PersistenceContext EntityManager em;

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

    @Test
    void pendingBootstrapAdminGetsFreshInviteOnRestart() {
        emptyUsers();
        bootstrap.bootstrap("again@boot.it");
        long id = jdbc.queryForObject("select id from app_user where email = 'again@boot.it'", Long.class);
        // stands in for the token in the invite mail that never arrived
        String lost = tokens.issue(TokenPurpose.INVITE, "USER", id, java.time.Duration.ofHours(1), null);
        jdbc.update("update app_user set invite_email_status = 'FAILED' where id = ?", id);
        em.clear(); // the JDBC update bypassed the persistence context

        bootstrap.bootstrap("again@boot.it");

        assertThat(tokens.redeem(TokenPurpose.INVITE, lost)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from one_time_token where subject_id = ? and used_at is null",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select invite_email_status from app_user where id = ?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void noRecoveryOnceAnotherAdminHasPassword() {
        emptyUsers();
        jdbc.update("insert into app_user (email, first_name, last_name, role, password_hash) values "
                + "('live@boot.it', 'L', 'A', 'ADMIN', 'x')");
        jdbc.update("insert into app_user (email, first_name, last_name, role) values "
                + "('pending@boot.it', 'P', 'A', 'ADMIN')");
        long id = jdbc.queryForObject("select id from app_user where email = 'pending@boot.it'", Long.class);
        String token = tokens.issue(TokenPurpose.INVITE, "USER", id, java.time.Duration.ofHours(1), null);

        bootstrap.bootstrap("pending@boot.it");

        assertThat(tokens.redeem(TokenPurpose.INVITE, token)).isPresent();
    }
}
