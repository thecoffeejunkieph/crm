package ph.thecoffeejunkie.crm.notifications;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.PageQuery;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class NotificationsIT {

    static final String USERS = "(select id from app_user where email like '%@notif.it')";

    @Autowired Notifications notifications;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    long sales;
    long otherSales;
    long archivedSales;

    @BeforeEach
    void setUp() {
        clean();
        sales = user("sales@notif.it", false);
        otherSales = user("other@notif.it", false);
        archivedSales = user("gone@notif.it", true);
    }

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
        // toRole also reaches other tests' sales users, so clean by marker, not by our users.
        jdbc.update("delete from notification where title like 'NT:%'");
        jdbc.update("delete from app_user where email like '%@notif.it'");
    }

    @Test
    void toRoleSkipsArchivedUsers() {
        tx.executeWithoutResult(s -> notifications.toRole(Role.SALES, NotificationType.APPROVAL_REQUESTED,
                "quote", 5L, "NT:Needs approval", "body"));

        assertThat(count(sales)).isEqualTo(1);
        assertThat(count(otherSales)).isEqualTo(1);
        assertThat(count(archivedSales)).isZero();
    }

    @Test
    void notRetainedWhenTransactionRollsBack() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            notifications.toUser(sales, NotificationType.APPROVAL_DECIDED, "quote", 5L, "NT:Decided", null);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count(sales)).isZero();
        assertThat(notifications.unreadCount(sales)).isZero();
    }

    @Test
    void listCountAndMarkRead() throws Exception {
        tx.executeWithoutResult(s -> {
            notifications.toUser(sales, NotificationType.APPROVAL_REQUESTED, "quote", 1L, "NT:One", null);
            notifications.toUser(sales, NotificationType.APPROVAL_DECIDED, "quote", 2L, "NT:Two", "b");
        });
        assertThat(notifications.unreadCount(sales)).isEqualTo(2);

        var page = notifications.list(sales, false, new PageQuery(null, null, null));
        assertThat(page.content()).extracting(NotificationResponse::title).containsExactly("NT:Two", "NT:One");

        notifications.markRead(sales, page.content().get(0).id());
        assertThat(notifications.unreadCount(sales)).isEqualTo(1);
        notifications.markAllRead(sales);
        assertThat(notifications.unreadCount(sales)).isZero();

        mvc.perform(get("/api/notifications/unread-count").with(as(sales)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/notifications").with(as(sales)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].read").value(true));
    }

    @Test
    void cannotReadOthersNotification() throws Exception {
        tx.executeWithoutResult(s ->
                notifications.toUser(otherSales, NotificationType.APPROVAL_DECIDED, "quote", 5L, "NT:Private", null));
        long id = jdbc.queryForObject("select id from notification where recipient_user_id = ?", Long.class, otherSales);

        mvc.perform(post("/api/notifications/" + id + "/read").with(as(sales)).with(csrf(mvc)))
                .andExpect(status().isNotFound());
        assertThat(notifications.unreadCount(otherSales)).isEqualTo(1);

        mvc.perform(post("/api/notifications/" + id + "/read").with(as(otherSales)).with(csrf(mvc)))
                .andExpect(status().isNoContent());
        assertThat(notifications.unreadCount(otherSales)).isZero();
    }

    @Test
    void readAllOnlyTouchesOwnRows() throws Exception {
        tx.executeWithoutResult(s -> {
            notifications.toUser(sales, NotificationType.APPROVAL_DECIDED, "quote", 1L, "NT:Mine", null);
            notifications.toUser(otherSales, NotificationType.APPROVAL_DECIDED, "quote", 1L, "NT:Theirs", null);
        });

        mvc.perform(post("/api/notifications/read-all").with(as(sales)).with(csrf(mvc)))
                .andExpect(status().isNoContent());

        assertThat(notifications.unreadCount(sales)).isZero();
        assertThat(notifications.unreadCount(otherSales)).isEqualTo(1);
    }

    @Test
    void actorIsNotNotifiedOfOwnAction() {
        SecurityContextHolder.getContext().setAuthentication(token(sales));
        tx.executeWithoutResult(s -> notifications.toUsers(List.of(sales, otherSales),
                NotificationType.ORDER_RELEASED, "ORDER", 1L, "NT:Released", null));
        assertThat(count(sales)).isZero();
        assertThat(count(otherSales)).isEqualTo(1);
    }

    @Test
    void noStaffActorDropsNobody() {
        tx.executeWithoutResult(s -> notifications.toUser(sales, NotificationType.ORDER_RELEASED, "ORDER", 1L,
                "NT:Sys", null));
        assertThat(count(sales)).isEqualTo(1);
    }

    @Test
    void toUsersDeduplicates() {
        tx.executeWithoutResult(s -> notifications.toUsers(List.of(sales, sales), NotificationType.LOW_STOCK,
                "PRODUCT", 1L, "NT:Low", null));
        assertThat(count(sales)).isEqualTo(1);
    }

    @Test
    void unreadFilterListsOnlyUnread() throws Exception {
        tx.executeWithoutResult(s -> {
            notifications.toUser(sales, NotificationType.APPROVAL_DECIDED, "quote", 1L, "NT:Old", null);
            notifications.toUser(sales, NotificationType.APPROVAL_DECIDED, "quote", 2L, "NT:New", null);
        });
        long old = jdbc.queryForObject("select id from notification where title = 'NT:Old'", Long.class);
        notifications.markRead(sales, old);

        mvc.perform(get("/api/notifications").param("unread", "true").with(as(sales)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("NT:New"))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    void streamRequiresSession() throws Exception {
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }

    private long count(long userId) {
        return jdbc.queryForObject("select count(*) from notification where recipient_user_id = ?", Long.class, userId);
    }

    private long user(String email, boolean archived) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role, archived_at) "
                + "values (?, 'N', 'U', 'SALES', " + (archived ? "now()" : "null") + ") returning id", Long.class, email);
    }

    private static RequestPostProcessor as(long userId) {
        return authentication(token(userId));
    }

    private static UsernamePasswordAuthenticationToken token(long userId) {
        return (new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(userId, "u" + userId + "@notif.it", "N U", Role.SALES, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_SALES"))));
    }
}
