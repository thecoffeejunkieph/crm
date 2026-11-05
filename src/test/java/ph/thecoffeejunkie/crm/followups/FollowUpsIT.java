package ph.thecoffeejunkie.crm.followups;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.followups.FollowUpsFixture.as;

@IntegrationTest
class FollowUpsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    FollowUpsFixture f;
    long admin;
    long rep;
    long other;
    long acme;
    long bolt;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        acme = f.customer("acme", rep);
        bolt = f.customer("bolt", other);
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions create(RequestPostProcessor who, String body) throws Exception {
        return mvc.perform(f.json(post("/api/follow-ups"), body).with(who));
    }

    private String body(long customer, Object extra) {
        return "{\"customerId\":" + customer + ",\"title\":\"Call back\",\"dueDate\":\"" + f.today + "\""
                + (extra == null ? "" : "," + extra) + "}";
    }

    @Test
    void repCreatesForThemself() throws Exception {
        create(asRep, body(acme, "\"assigneeId\":" + other)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeId").value(rep))
                .andExpect(jsonPath("$.dueDate").value(f.today.toString()));
    }

    @Test
    void repCannotCreateOnOthersCustomer() throws Exception {
        create(asRep, body(bolt, null)).andExpect(status().isNotFound());
    }

    @Test
    void adminDefaultsToCustomersRep() throws Exception {
        create(asAdmin, body(bolt, null)).andExpect(status().isCreated()).andExpect(jsonPath("$.assigneeId").value(other));
    }

    @Test
    void adminCustomerWithoutRep422() throws Exception {
        long none = f.customer("none", null);
        create(asAdmin, body(none, null)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_NO_ASSIGNEE"));
    }

    @Test
    void adminNonRepAssignee422() throws Exception {
        create(asAdmin, body(acme, "\"assigneeId\":" + admin)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_NOT_A_REP"));
    }

    @Test
    void orderOfOtherCustomer422() throws Exception {
        long boltOrder = f.order(bolt);
        create(asAdmin, body(acme, "\"orderId\":" + boltOrder)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_ORDER_MISMATCH"));
    }

    @Test
    void blankTitle400() throws Exception {
        create(asRep, "{\"customerId\":" + acme + ",\"title\":\"  \",\"dueDate\":\"" + f.today + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void bucketsSplitByDueDate() throws Exception {
        long late = f.followUp(acme, null, rep, "late", f.today.minusDays(1), null);
        long now = f.followUp(acme, null, rep, "now", f.today, null);
        long soon = f.followUp(acme, null, rep, "soon", f.today.plusDays(1), null);
        long doneOld = f.followUp(acme, null, rep, "old", f.today, Instant.now().minus(2, ChronoUnit.DAYS));
        long doneNew = f.followUp(acme, null, rep, "new", f.today, Instant.now().minus(1, ChronoUnit.HOURS));
        expectIds("overdue", late);
        expectIds("today", now);
        expectIds("upcoming", soon);
        expectIds("done", doneNew, doneOld);
        mvc.perform(get("/api/follow-ups").param("bucket", "soon").with(asRep)).andExpect(status().isBadRequest());
    }

    private void expectIds(String bucket, Long... ids) throws Exception {
        mvc.perform(get("/api/follow-ups").param("bucket", bucket).param("customerId", String.valueOf(acme)).with(asRep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(java.util.Arrays.stream(ids).map(Long::intValue).toArray())));
    }

    @Test
    void repSeesOnlyOwn() throws Exception {
        f.followUp(acme, null, rep, "mine", f.today, null);
        f.followUp(bolt, null, other, "theirs", f.today, null);
        mvc.perform(get("/api/follow-ups").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].title", contains("mine")));
    }

    @Test
    void adminFiltersByAssignee() throws Exception {
        f.followUp(acme, null, rep, "mine", f.today, null);
        f.followUp(bolt, null, other, "theirs", f.today, null);
        mvc.perform(get("/api/follow-ups").param("assigneeId", String.valueOf(other))
                        .param("customerId", String.valueOf(bolt)).with(asAdmin))
                .andExpect(jsonPath("$.content[*].title", contains("theirs")));
        // A rep's assigneeId filter is ignored: they only ever see their own.
        mvc.perform(get("/api/follow-ups").param("assigneeId", String.valueOf(other)).with(asRep))
                .andExpect(jsonPath("$.content[*].title", contains("mine")));
    }

    @Test
    void customerAndOrderFilters() throws Exception {
        long order = f.order(acme);
        f.followUp(acme, order, rep, "on order", f.today, null);
        f.followUp(acme, null, rep, "plain", f.today, null);
        mvc.perform(get("/api/follow-ups").param("orderId", String.valueOf(order)).with(asRep))
                .andExpect(jsonPath("$.content[*].title", contains("on order")));
        mvc.perform(get("/api/follow-ups").param("customerId", String.valueOf(acme)).with(asRep))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void countsMatchBuckets() throws Exception {
        f.followUp(acme, null, rep, "late", f.today.minusDays(3), null);
        f.followUp(acme, null, rep, "now", f.today, null);
        f.followUp(acme, null, rep, "soon", f.today.plusDays(2), null);
        f.followUp(acme, null, rep, "done", f.today, Instant.now());
        f.followUp(bolt, null, other, "theirs", f.today, null);
        mvc.perform(get("/api/follow-ups/counts").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(1)).andExpect(jsonPath("$.today").value(1))
                .andExpect(jsonPath("$.upcoming").value(1));
    }

    @Test
    void createIsAudited() throws Exception {
        String id = com.jayway.jsonpath.JsonPath.read(create(asRep, body(acme, null)).andReturn().getResponse()
                .getContentAsString(), "$.id").toString();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action = 'FOLLOW_UP_CREATED' "
                + "and entity_type = 'FOLLOW_UP' and entity_id = ?", Integer.class, Long.parseLong(id))).isEqualTo(1);
    }

    @Test
    void responseNamesOrderAndCustomer() throws Exception {
        long order = f.order(acme);
        create(asRep, body(acme, "\"orderId\":" + order)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(f.orderNumber(order)))
                .andExpect(jsonPath("$.customerName").value(f.tag + "acme"))
                .andExpect(jsonPath("$.assigneeName").value(org.hamcrest.Matchers.startsWith(f.tag + "rep")));
    }

    @Test
    void warehouse403() throws Exception {
        long wh = f.user("wh", Role.WAREHOUSE);
        mvc.perform(get("/api/follow-ups").with(as(wh, Role.WAREHOUSE))).andExpect(status().isForbidden());
    }
}
