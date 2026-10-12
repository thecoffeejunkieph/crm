package ph.thecoffeejunkie.crm.notifications;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.approvals.ApprovalType;
import ph.thecoffeejunkie.crm.approvals.Approvals;
import ph.thecoffeejunkie.crm.approvals.CreateApprovalRequest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Lives in the notifications package because NotificationStreams is package-private. */
@IntegrationTest
class ApprovalPushAfterCommitIT {

    static final String SUBJECT = "apr-push-quote";

    @Autowired Approvals approvals;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @MockitoSpyBean NotificationStreams streams;

    long sales;
    long admin1;
    long admin2;

    @BeforeEach
    void setUp() {
        clean();
        // Other test classes may leave ADMINs behind; count what is actually active.
        admin1 = user("a1@push.it", "ADMIN");
        admin2 = user("a2@push.it", "ADMIN");
        sales = user("s@push.it", "SALES");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(sales, "s@push.it", "S U", Role.SALES, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_SALES"))));
        Mockito.clearInvocations(streams);
    }

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from notification where entity_type = 'APPROVAL' and entity_id in "
                + "(select id from approval_request where subject_type = ?)", SUBJECT);
        jdbc.update("delete from approval_request where subject_type = ?", SUBJECT);
        jdbc.update("delete from app_user where email like '%@push.it'");
    }

    @Test
    void pushedOnlyAfterCommit() {
        // Rolled back: nothing is pushed and nothing is stored.
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            approvals.request(req(1));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        verify(streams, never()).publish(anyLong(), any());
        assertThat(jdbc.queryForObject("select count(*) from approval_request where subject_type = ?",
                Long.class, SUBJECT)).isZero();

        // Committed: one push per active ADMIN.
        tx.executeWithoutResult(s -> approvals.request(req(2)));
        long admins = jdbc.queryForObject("select count(*) from app_user where role = 'ADMIN' and archived_at is null",
                Long.class);
        verify(streams, times((int) admins)).publish(anyLong(), any());
        verify(streams).publish(Mockito.eq(admin1), any());
        verify(streams).publish(Mockito.eq(admin2), any());
    }

    private static CreateApprovalRequest req(long subjectId) {
        return new CreateApprovalRequest(ApprovalType.DISCOUNT, SUBJECT, subjectId, 1, Map.of("pct", 5), "why");
    }

    private long user(String email, String role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, 'P', 'U', ?) returning id", Long.class, email, role);
    }
}
