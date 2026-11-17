package ph.thecoffeejunkie.crm.sales;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.as;
import static ph.thecoffeejunkie.crm.sales.SalesFixture.line;

/** Lives in the sales package to reuse SalesFixture; exercises shared.SortRequest through GET /api/orders. */
@IntegrationTest
class SortRequestIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    SalesFixture f;
    RequestPostProcessor asAdmin;
    long product;

    @BeforeEach
    void seed() {
        f = new SalesFixture(jdbc, mvc);
        asAdmin = as(f.user("adm", Role.ADMIN), Role.ADMIN);
        product = f.product("A", "112.00", "50.00", "VATABLE");
    }

    @AfterEach
    void cleanUp() {
        f.cleanUp();
    }

    /** Walks every page of 3 and returns this test's rows as "id:customerName". */
    private List<String> allPages(String sort) throws Exception {
        List<String> mine = new ArrayList<>();
        for (int page = 1;; page++) {
            String body = SalesFixture.body(mvc.perform(get("/api/orders").param("size", "3")
                    .param("page", String.valueOf(page)).param("sort", sort).with(asAdmin)).andExpect(status().isOk()));
            List<Number> ids = JsonPath.read(body, "$.content[*].id");
            List<String> names = JsonPath.read(body, "$.content[*].customerName");
            for (int i = 0; i < ids.size(); i++) {
                if (names.get(i).startsWith(f.tag)) {
                    mine.add(ids.get(i) + ":" + names.get(i));
                }
            }
            if (page >= (Integer) JsonPath.read(body, "$.totalPages")) {
                return mine;
            }
        }
    }

    @Test
    void sortByLinkedNamePagesConsistently() throws Exception {
        for (String n : List.of("B", "A", "C", "E", "D", "G", "F")) {
            f.createdOrder(asAdmin, f.customer(n, null), line(product, "1"));
        }
        List<String> got = allPages("customer,asc");
        assertThat(got).hasSize(7).doesNotHaveDuplicates();
        assertThat(got.stream().map(s -> s.substring(s.indexOf(':') + 1)).toList())
                .containsExactly(f.tag + "A", f.tag + "B", f.tag + "C", f.tag + "D", f.tag + "E", f.tag + "F", f.tag + "G");
    }

    @Test
    void tieBreakerKeepsPagesStable() throws Exception {
        for (int i = 0; i < 2; i++) {
            long c1 = f.customer("Acme", null), c2 = f.customer("Acme", null);
            f.createdOrder(asAdmin, c1, line(product, "1"));
            f.createdOrder(asAdmin, c2, line(product, "1"));
        }
        List<Long> ids = allPages("customer,asc").stream()
                .map(s -> Long.parseLong(s.substring(0, s.indexOf(':')))).toList();
        assertThat(ids).hasSize(4).isSorted();
    }

    @Test
    void unknownSortKey400() throws Exception {
        mvc.perform(get("/api/orders").param("sort", "nope,asc").with(asAdmin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
