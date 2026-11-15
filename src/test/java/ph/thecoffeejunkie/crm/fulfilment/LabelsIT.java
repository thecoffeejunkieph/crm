package ph.thecoffeejunkie.crm.fulfilment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.inventory.InventoryFixture;
import ph.thecoffeejunkie.crm.sales.PdfText;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.inventory.InventoryFixture.as;

@IntegrationTest
class LabelsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationContext ctx;

    InventoryFixture f;
    long wh;
    long otherWh;
    long p;
    RequestPostProcessor admin;
    RequestPostProcessor whUser;
    RequestPostProcessor sales;

    @BeforeEach
    void seed() {
        f = InventoryFixture.create(jdbc, tx, ctx);
        wh = f.warehouse("W");
        otherWh = f.warehouse("O");
        p = f.product("P", "KG");
        admin = as(f.user("adm"), Role.ADMIN);
        whUser = as(f.user("wh"), Role.WAREHOUSE, wh);
        sales = as(f.user("rep"), Role.SALES);
    }

    @AfterEach
    void clean() {
        f.cleanUp();
    }

    @Test
    void pdfContainsNumber() throws Exception {
        byte[] pdf = mvc.perform(get("/api/labels").param("type", "PR").param("ids", "" + p).with(whUser))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(PdfText.of(pdf)).contains(f.tag + "P").contains("Product P");
        byte[] wpdf = mvc.perform(get("/api/labels").param("type", "WH").param("ids", wh + "," + otherWh).with(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(PdfText.of(wpdf)).contains(f.tag + "W").contains(f.tag + "O");
    }

    @Test
    void over100Rejected() throws Exception {
        String ids = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));
        mvc.perform(get("/api/labels").param("type", "PR").param("ids", ids).with(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownId404() throws Exception {
        mvc.perform(get("/api/labels").param("type", "DO").param("ids", "99999999").with(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void salesForbidden() throws Exception {
        mvc.perform(get("/api/labels").param("type", "PR").param("ids", "" + p).with(sales))
                .andExpect(status().isForbidden());
    }
}
