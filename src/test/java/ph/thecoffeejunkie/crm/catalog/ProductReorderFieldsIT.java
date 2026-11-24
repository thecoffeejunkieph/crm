package ph.thecoffeejunkie.crm.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ph.thecoffeejunkie.crm.IntegrationTest;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class ProductReorderFieldsIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private final String tag = "RF" + UUID.randomUUID().toString().substring(0, 8);
    long admin;
    long activeSupplier;
    long archivedSupplier;

    @BeforeEach
    void setUp() {
        admin = jdbc.queryForObject("with u as (insert into app_user (email, first_name, last_name) values (?, 'A', 'D') returning id) insert into user_role select id, 'ADMIN' from u returning user_id",
                Long.class, tag + "a@rf.it");
        activeSupplier = jdbc.queryForObject("insert into supplier (code, name) values (?, ?) returning id", Long.class, tag + "-A", tag + " active");
        archivedSupplier = jdbc.queryForObject("insert into supplier (code, name, archived_at) values (?, ?, now()) returning id", Long.class, tag + "-X", tag + " archived");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from product where sku like ?", tag + "%");
        jdbc.update("delete from supplier where code like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }

    private ResultActions create(String sku, String level, String target, Object supplier) throws Exception {
        String json = "{\"sku\":\"%s\",\"name\":\"%s beans\",\"unit\":\"KG\",\"price\":10,\"taxClass\":\"VATABLE\",\"reorderLevel\":%s,\"reorderTarget\":%s,\"preferredSupplierId\":%s}"
                .formatted(sku, tag, level, target, supplier);
        return mvc.perform(post("/api/products").with(authentication(new UsernamePasswordAuthenticationToken(
                        new StaffPrincipal(admin, "u@rf.it", "A U", Set.of(Role.ADMIN), Set.of()), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void savesPreferredSupplierAndTarget() throws Exception {
        create(tag + "-1", "10", "40", activeSupplier).andExpect(status().isOk())
                .andExpect(jsonPath("$.reorderTarget").value(40))
                .andExpect(jsonPath("$.preferredSupplierId").value(activeSupplier));
    }

    @Test
    void targetBelowReorderLevelIs400() throws Exception {
        create(tag + "-2", "10", "5", "null").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reorderTarget"));
    }

    @Test
    void unknownOrArchivedSupplierIs400() throws Exception {
        create(tag + "-3", "10", "40", archivedSupplier).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("preferredSupplierId"));
        create(tag + "-4", "10", "40", "999999999").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("preferredSupplierId"));
    }
}
