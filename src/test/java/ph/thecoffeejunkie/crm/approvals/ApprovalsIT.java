package ph.thecoffeejunkie.crm.approvals;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
@org.springframework.test.context.event.RecordApplicationEvents
class ApprovalsIT {

    static final String SUBJECT = "apr-it-quote";

    @Autowired Approvals approvals;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired org.springframework.test.context.event.ApplicationEvents events;

    long admin;
    long sales;
    long otherSales;

    @BeforeEach
    void setUp() {
        clean();
        admin = user("admin@apr.it", "ADMIN");
        sales = user("sales@apr.it", "SALES");
        otherSales = user("other@apr.it", "SALES");
    }

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from notification where recipient_user_id in (select id from app_user where email like '%@apr.it')");
        jdbc.update("delete from approval_request where subject_type = ?", SUBJECT);
        jdbc.update("delete from app_user where email like '%@apr.it'");
    }

    @Test
    void ensureApprovedThrowsWithDetails() {
        login(sales, Role.SALES);
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                approvals.ensureApproved(ApprovalType.DISCOUNT, SUBJECT, 7, 3, Map.of("pct", 15))))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.APPROVAL_REQUIRED);
                    assertThat(e.properties()).containsEntry("subjectId", 7L).containsEntry("subjectVersion", 3L)
                            .containsEntry("subjectType", SUBJECT).containsKey("requestedValue").containsEntry("approvalType", ApprovalType.DISCOUNT).doesNotContainKey("type");
                });
    }


    @org.springframework.web.bind.annotation.RestController
    static class Guarded {
        final Approvals approvals;
        Guarded(Approvals approvals) { this.approvals = approvals; }

        @org.springframework.web.bind.annotation.GetMapping("/guarded")
        void guarded() { approvals.ensureApproved(ApprovalType.DISCOUNT, SUBJECT, 7, 3, Map.of("pct", 15)); }
    }

    @Test
    void problemBodyKeepsApprovalTypeApartFromProblemType() throws Exception {
        login(sales, Role.SALES);
        var standalone = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new Guarded(approvals))
                .setControllerAdvice(new ph.thecoffeejunkie.crm.shared.ProblemDetailAdvice()).build();
        standalone.perform(get("/guarded")).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.approvalType").value("DISCOUNT"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"type\":\"DISCOUNT\""))));
    }
    @Test
    void approvedMatchPasses() throws Exception {
        long id = requestVia(sales, 7, 3);
        decide(id, true).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        login(sales, Role.SALES);
        tx.executeWithoutResult(s -> approvals.ensureApproved(ApprovalType.DISCOUNT, SUBJECT, 7, 3, Map.of("pct", 15)));
    }

    @Test
    void newerSubjectVersionNeedsNewApproval() throws Exception {
        long id = requestVia(sales, 7, 3);
        decide(id, true).andExpect(status().isOk());

        login(sales, Role.SALES);
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
                approvals.ensureApproved(ApprovalType.DISCOUNT, SUBJECT, 7, 4, Map.of("pct", 15))))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void adminSelfApprovesAndIsAudited() {
        login(admin, Role.ADMIN);
        tx.executeWithoutResult(s -> approvals.ensureApproved(ApprovalType.DISCOUNT, SUBJECT, 7, 3, Map.of("pct", 15)));

        assertThat(jdbc.queryForList("select summary from audit_event where action = 'SELF_APPROVED' "
                + "and entity_type = ? and entity_id = 7 and actor_id = ?", String.class, SUBJECT, admin))
                .containsExactly("self-approved (admin)");
    }

    @Test
    void duplicatePendingRequestReturnsSame() throws Exception {
        long first = requestVia(sales, 7, 3);
        long second = requestVia(otherSales, 7, 3);

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from approval_request where subject_type = ?",
                Long.class, SUBJECT)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification where type = 'APPROVAL_REQUESTED' "
                + "and entity_id = ? and recipient_user_id = ?", Long.class, first, admin)).isEqualTo(1);
    }

    @Test
    void decideNotifiesRequester() throws Exception {
        long id = requestVia(sales, 7, 3);
        decide(id, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedById").value(admin))
                .andExpect(jsonPath("$.requestedByName").value("A U"));

        assertThat(jdbc.queryForObject("select count(*) from notification where type = 'APPROVAL_DECIDED' "
                + "and entity_type = 'APPROVAL' and entity_id = ? and recipient_user_id = ?", Long.class, id, sales))
                .isEqualTo(1);
    }

    @Test
    void salesCannotDecide() throws Exception {
        long id = requestVia(sales, 7, 3);
        mvc.perform(post("/api/approvals/" + id + "/decision").with(as(sales, Role.SALES)).with(csrf(mvc))
                .contentType(APPLICATION_JSON).content("{\"approve\":true}")).andExpect(status().isForbidden());
    }

    @Test
    void cannotDecideTwice() throws Exception {
        long id = requestVia(sales, 7, 3);
        decide(id, true).andExpect(status().isOk());
        decide(id, false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void cancelIsRequesterOnlyAndPendingOnly() throws Exception {
        long id = requestVia(sales, 7, 3);
        mvc.perform(post("/api/approvals/" + id + "/cancel").with(as(otherSales, Role.SALES)).with(csrf(mvc)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/approvals/" + id + "/cancel").with(as(sales, Role.SALES)).with(csrf(mvc)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/approvals/" + id + "/cancel").with(as(sales, Role.SALES)).with(csrf(mvc)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from approval_request where id = ?", String.class, id))
                .isEqualTo("CANCELLED");
    }

    @Test
    void salesSeesOnlyOwnRequests() throws Exception {
        requestVia(sales, 7, 3);
        requestVia(otherSales, 8, 1);

        mvc.perform(get("/api/approvals").with(as(sales, Role.SALES)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subjectId").value(7));
        mvc.perform(get("/api/approvals?status=PENDING").with(as(admin, Role.ADMIN)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void concurrentIdenticalRequestsYieldOneRow() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        var gate = new java.util.concurrent.CountDownLatch(1);
        var req = new CreateApprovalRequest(ApprovalType.DISCOUNT, SUBJECT, 9, 1, Map.of("pct", 5), "r");
        List<java.util.concurrent.Future<Long>> fs = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            fs.add(pool.submit(() -> {
                login(sales, Role.SALES);
                gate.await();
                return approvals.request(req).id();
            }));
        }
        gate.countDown();
        var ids = new java.util.HashSet<Long>();
        for (var f : fs) {
            ids.add(f.get());
        }
        pool.shutdown();
        assertThat(ids).hasSize(1);
    }

    private static RequiredApproval req(ApprovalType type, long version) {
        return new RequiredApproval(type, SUBJECT, 7, version, Map.of("v", version));
    }

    private void approvedRow(ApprovalType type, long version) {
        jdbc.update("insert into approval_request (type, subject_type, subject_id, subject_version, requested_value, "
                + "reason, status, requested_by) values (?, ?, 7, ?, '{}', 'r', 'APPROVED', ?)",
                type.name(), SUBJECT, version, sales);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ensureAllListsEveryMissing() {
        approvedRow(ApprovalType.DISCOUNT, 5);
        login(sales, Role.SALES);
        var required = List.of(req(ApprovalType.DISCOUNT, 5), req(ApprovalType.BELOW_COST, 5), req(ApprovalType.CREDIT_LIMIT, 5));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> approvals.ensureAllApproved(required)))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.APPROVAL_REQUIRED);
                    assertThat(e.properties()).containsEntry("approvalType", ApprovalType.BELOW_COST)
                            .containsEntry("subjectVersion", 5L);
                    var list = (List<Map<String, Object>>) e.properties().get("approvals");
                    assertThat(list).hasSize(2);
                    assertThat(list.get(1)).containsEntry("approvalType", ApprovalType.CREDIT_LIMIT)
                            .containsEntry("subjectType", SUBJECT).containsEntry("subjectId", 7L)
                            .containsEntry("requestedValue", Map.of("v", 5L));
                });
    }

    @Test
    void adminSelfApprovesEach() {
        login(admin, Role.ADMIN);
        tx.executeWithoutResult(s -> approvals.ensureAllApproved(
                List.of(req(ApprovalType.DISCOUNT, 1), req(ApprovalType.BELOW_COST, 1))));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'SELF_APPROVED' "
                + "and entity_type = ? and actor_id = ?", Integer.class, SUBJECT, admin)).isEqualTo(2);
    }

    @Test
    void missingIgnoresAdmin() {
        login(admin, Role.ADMIN);
        var required = List.of(req(ApprovalType.DISCOUNT, 1), req(ApprovalType.BELOW_COST, 1));
        List<RequiredApproval> missing = tx.execute(s -> approvals.missing(required));
        assertThat(missing).isEqualTo(required);
    }

    @Test
    void requestOnBehalfStoresGivenRequester() {
        login(admin, Role.ADMIN);
        var r = approvals.requestOnBehalf(new CreateApprovalRequest(ApprovalType.DISCOUNT, SUBJECT, 7, 1,
                Map.of("pct", 20), "Customer accepted via portal"), sales);
        assertThat(jdbc.queryForObject("select requested_by from approval_request where id = ?", Long.class, r.id()))
                .isEqualTo(sales);
    }

    @Test
    void decidePublishesEvent() throws Exception {
        long id = requestVia(sales, 7, 3);
        decide(id, true).andExpect(status().isOk());
        assertThat(events.stream(ApprovalDecided.class).toList())
                .containsExactly(new ApprovalDecided(id, ApprovalType.DISCOUNT, SUBJECT, 7, 3, ApprovalStatus.APPROVED));
    }

    @Test
    void invoiceVoidTypeAccepted() throws Exception {
        login(sales, Role.SALES);
        var r = approvals.request(new CreateApprovalRequest(ApprovalType.INVOICE_VOID, SUBJECT, 7, 1,
                Map.of("invoice", "SI-00001"), "wrong TIN"));
        assertThat(r.id()).isPositive();
    }

    private long requestVia(long userId, long subjectId, long subjectVersion) throws Exception {
        String body = "{\"type\":\"DISCOUNT\",\"subjectType\":\"" + SUBJECT + "\",\"subjectId\":" + subjectId
                + ",\"subjectVersion\":" + subjectVersion + ",\"requestedValue\":{\"pct\":15},\"reason\":\"loyal\"}";
        String json = mvc.perform(post("/api/approvals").with(as(userId, Role.SALES)).with(csrf(mvc))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions decide(long id, boolean approve) throws Exception {
        return mvc.perform(post("/api/approvals/" + id + "/decision").with(as(admin, Role.ADMIN)).with(csrf(mvc))
                .contentType(APPLICATION_JSON).content("{\"approve\":" + approve + ",\"note\":\"n\"}"));
    }

    private void login(long userId, Role role) {
        SecurityContextHolder.getContext().setAuthentication(token(userId, role));
    }

    private long user(String email, String role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, 'A', 'U', ?) returning id", Long.class, email, role);
    }

    private static UsernamePasswordAuthenticationToken token(long userId, Role role) {
        return new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(userId, "u" + userId + "@apr.it", "A U", role, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private static RequestPostProcessor as(long userId, Role role) {
        return authentication(token(userId, role));
    }
}
