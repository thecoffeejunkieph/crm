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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;
import static ph.thecoffeejunkie.crm.followups.FollowUpsFixture.as;

@IntegrationTest
class FollowUpEditIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    FollowUpsFixture f;
    long admin;
    long rep;
    long other;
    long acme;
    long fu;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new FollowUpsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        acme = f.customer("acme", rep);
        fu = f.followUp(acme, null, rep, "Call back", f.today, null);
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private long version() {
        return jdbc.queryForObject("select version from follow_up where id = ?", Long.class, fu);
    }

    private int audits(String action) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and entity_type = 'FOLLOW_UP' "
                + "and entity_id = ?", Integer.class, action, fu);
    }

    private ResultActions edit(RequestPostProcessor who, String extra, long version) throws Exception {
        return mvc.perform(f.json(put("/api/follow-ups/" + fu), "{\"customerId\":" + acme
                + ",\"title\":\"Call Monday\",\"dueDate\":\"" + f.today.plusDays(3) + "\",\"version\":" + version
                + (extra == null ? "" : "," + extra) + "}").with(who));
    }

    private ResultActions action(RequestPostProcessor who, String what) throws Exception {
        return mvc.perform(post("/api/follow-ups/" + fu + "/" + what).with(csrf(mvc)).with(who));
    }

    @Test
    void assigneeEditsTitleAndDate() throws Exception {
        edit(asRep, null, version()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Call Monday"))
                .andExpect(jsonPath("$.dueDate").value(f.today.plusDays(3).toString()));
        assertThat(audits("FOLLOW_UP_UPDATED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select changes::text from audit_event where action = 'FOLLOW_UP_UPDATED' "
                + "and entity_id = ?", String.class, fu)).contains("title").contains("dueDate");
    }

    @Test
    void staleVersion409() throws Exception {
        long v = version();
        edit(asRep, null, v).andExpect(status().isOk());
        edit(asRep, null, v).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void repCannotChangeAssignee() throws Exception {
        edit(asRep, "\"assigneeId\":" + other, version()).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(rep));
        edit(asAdmin, "\"assigneeId\":" + admin, version()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_NOT_A_REP"));
        edit(asAdmin, "\"assigneeId\":" + other, version()).andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(other));
        assertThat(audits("FOLLOW_UP_REASSIGNED")).isEqualTo(1);
    }

    @Test
    void otherRep404() throws Exception {
        RequestPostProcessor asOther = as(other, Role.SALES);
        edit(asOther, null, version()).andExpect(status().isNotFound());
        action(asOther, "done").andExpect(status().isNotFound());
        action(asOther, "reopen").andExpect(status().isNotFound());
    }

    @Test
    void doneThenDoneAgainIsNoop() throws Exception {
        String first = action(asRep, "done").andExpect(status().isOk()).andExpect(jsonPath("$.doneAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String doneAt = com.jayway.jsonpath.JsonPath.read(first, "$.doneAt");
        action(asRep, "done").andExpect(status().isOk()).andExpect(jsonPath("$.doneAt").value(doneAt));
        assertThat(audits("FOLLOW_UP_DONE")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select done_by from follow_up where id = ?", Long.class, fu)).isEqualTo(rep);
    }

    /** Tab B's tick lands while tab A's is still committing: B must get 200, not 409. */
    @Test
    void concurrentDoneIsNoop() throws Exception {
        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support
                .TransactionTemplate(txManager);
        Thread tabA = new Thread(() -> tx.executeWithoutResult(s -> {
            jdbc.update("update follow_up set done_at = now(), done_by = ?, version = version + 1 where id = ?", rep, fu);
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        tabA.start();
        locked.await();
        var tabB = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return action(asRep, "done").andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(1000); // let tab B read the old row and block on A's row lock
        release.countDown();
        tabA.join();
        assertThat(tabB.get()).isEqualTo(200);
    }

    @Test
    void reopenClearsDone() throws Exception {
        action(asRep, "done").andExpect(status().isOk());
        action(asAdmin, "reopen").andExpect(status().isOk()).andExpect(jsonPath("$.doneAt").isEmpty());
        assertThat(jdbc.queryForObject("select done_by from follow_up where id = ?", Long.class, fu)).isNull();
        assertThat(audits("FOLLOW_UP_REOPENED")).isEqualTo(1);
        action(asAdmin, "reopen").andExpect(status().isOk());
        assertThat(audits("FOLLOW_UP_REOPENED")).isEqualTo(1);
    }

    @Test
    void editOrderMustMatchCustomer() throws Exception {
        long elsewhere = f.order(f.customer("bolt", other));
        edit(asRep, "\"orderId\":" + elsewhere, version()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FOLLOW_UP_ORDER_MISMATCH"));
    }

    @Test
    void editWithoutVersion400() throws Exception {
        mvc.perform(f.json(put("/api/follow-ups/" + fu), "{\"customerId\":" + acme
                + ",\"title\":\"x\",\"dueDate\":\"" + f.today + "\"}").with(asRep)).andExpect(status().isBadRequest());
    }
}
