package ph.thecoffeejunkie.crm.deals;

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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.deals.DealsFixture.as;
import static ph.thecoffeejunkie.crm.deals.DealsFixture.id;

@IntegrationTest
class DealsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    DealsFixture f;
    long admin;
    long rep;
    long other;
    long wh;
    long acme;
    long bolt;
    RequestPostProcessor asRep;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void seed() {
        f = new DealsFixture(jdbc, mvc);
        admin = f.user("adm", Role.ADMIN);
        rep = f.user("rep", Role.SALES);
        other = f.user("oth", Role.SALES);
        wh = f.user("wh", Role.WAREHOUSE);
        acme = f.customer("Acme", rep);
        bolt = f.customer("Bolt", other);
        asRep = as(rep, Role.SALES);
        asAdmin = as(admin, Role.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    private ResultActions create(RequestPostProcessor who, long customerId, String extra) throws Exception {
        return mvc.perform(f.json(post("/api/deals"), "{\"customerId\":" + customerId + ",\"title\":\"" + f.tag
                + "Monthly beans\"" + extra + "}").with(who));
    }

    @Test
    void repCreatesOnOwnCustomer() throws Exception {
        long id = id(create(asRep, acme, ",\"value\":50000,\"expectedClose\":\"2026-12-01\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repId").value(rep))
                .andExpect(jsonPath("$.customerName").value(f.tag + "Acme"))
                .andExpect(jsonPath("$.stage").value("DISCOVERY"))
                .andExpect(jsonPath("$.value").value(50000)));
        assertThat(f.audits("DEAL_CREATED", id)).isEqualTo(1);
    }

    @Test
    void cannotCreateOnHiddenOrArchivedCustomer() throws Exception {
        create(asRep, bolt, "").andExpect(status().isNotFound());
        f.archive(acme);
        create(asRep, acme, "").andExpect(status().isNotFound());
    }

    @Test
    void unassignedCustomersDealIsAdminOnly() throws Exception {
        long loose = f.customer("Loose", null);
        long id = id(create(asAdmin, loose, "").andExpect(status().isCreated())
                .andExpect(jsonPath("$.repId").isEmpty()));
        mvc.perform(get("/api/deals/" + id).with(asRep)).andExpect(status().isNotFound());
    }

    @Test
    void repSeesOnlyOwnAdminFiltersByRep() throws Exception {
        long mine = f.deal(acme, rep, "Mine", "DISCOVERY", null, null);
        long theirs = f.deal(bolt, other, "Theirs", "DISCOVERY", null, null);
        mvc.perform(get("/api/deals/" + theirs).with(asRep)).andExpect(status().isNotFound());
        mvc.perform(get("/api/deals?q=" + f.tag).with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) mine)));
        mvc.perform(get("/api/deals?q=" + f.tag).with(asAdmin))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) mine, (int) theirs)));
        mvc.perform(get("/api/deals?q=" + f.tag + "&repId=" + other).with(asAdmin))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) theirs)));
    }

    @Test
    void defaultListIsOpenAndStageFilters() throws Exception {
        long open = f.deal(acme, rep, "Open", "PROPOSAL", null, null);
        f.deal(acme, rep, "Won", "WON", null, null);
        long lost = f.deal(acme, rep, "Lost", "LOST", null, null);
        mvc.perform(get("/api/deals?q=" + f.tag).with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) open)));
        mvc.perform(get("/api/deals?stage=LOST&q=" + f.tag).with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) lost)));
        mvc.perform(get("/api/deals?q=" + f.tag.toLowerCase() + "acme").with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) open)));
    }

    @Test
    void customerNameSearchIsNotCutOffByOtherRepsCustomers() throws Exception {
        jdbc.update("insert into customer (type, display_name, payment_terms, assigned_rep_id) "
                + "select 'INDIVIDUAL', ?, 'NET_30', ? from generate_series(1, 501)", f.tag + "Cafe other", other);
        long mine = f.customer("Cafe mine", rep);
        long deal = f.deal(mine, rep, "Espresso", "DISCOVERY", null, null);
        mvc.perform(get("/api/deals?q=" + f.tag + "cafe").with(asRep))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder((int) deal)));
    }

    @Test
    void listSortsByExpectedCloseNullsLast() throws Exception {
        long none = f.deal(acme, rep, "None", "DISCOVERY", null, null);
        long late = f.deal(acme, rep, "Late", "DISCOVERY", null, LocalDate.of(2027, 3, 1));
        long soon = f.deal(acme, rep, "Soon", "DISCOVERY", null, LocalDate.of(2026, 12, 1));
        mvc.perform(get("/api/deals?q=" + f.tag).with(asRep))
                .andExpect(jsonPath("$.content[*].id", contains((int) soon, (int) late, (int) none)));
    }

    @Test
    void summaryTotalsWithinScope() throws Exception {
        f.deal(acme, rep, "A", "DISCOVERY", new BigDecimal("1000.50"), null);
        f.deal(acme, rep, "B", "DISCOVERY", null, null);
        f.deal(acme, rep, "C", "PROPOSAL", new BigDecimal("250"), null);
        f.deal(acme, rep, "D", "WON", new BigDecimal("999"), null);
        f.deal(bolt, other, "E", "DISCOVERY", new BigDecimal("77"), null);
        mvc.perform(get("/api/deals/summary").with(asRep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(3))
                .andExpect(jsonPath("$.openValue").value(1250.5))
                .andExpect(jsonPath("$.stages[?(@.stage=='DISCOVERY')].count").value(2))
                .andExpect(jsonPath("$.stages[?(@.stage=='DISCOVERY')].value").value(1000.5))
                .andExpect(jsonPath("$.stages[?(@.stage=='WON')].value").value(999.0))
                .andExpect(jsonPath("$.stages[?(@.stage=='LOST')].count").value(0));
    }

    @Test
    void invalidBodies400() throws Exception {
        create(asRep, acme, ",\"value\":-1").andExpect(status().isBadRequest());
        mvc.perform(f.json(post("/api/deals"), "{\"customerId\":" + acme + ",\"title\":\" \"}").with(asRep))
                .andExpect(status().isBadRequest());
        mvc.perform(f.json(post("/api/deals"), "{\"title\":\"x\"}").with(asRep)).andExpect(status().isBadRequest());
    }

    @Test
    void warehouseForbidden() throws Exception {
        mvc.perform(get("/api/deals").with(as(wh, Role.WAREHOUSE))).andExpect(status().isForbidden());
    }
}
