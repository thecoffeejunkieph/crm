package ph.thecoffeejunkie.crm.customers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ph.thecoffeejunkie.crm.CsrfSupport.csrf;

@IntegrationTest
class CustomersIT {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Customers customers;

    private final String tag = "CU" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from customer_contact where customer_id in (select id from customer where display_name like ?)", tag + "%");
        jdbc.update("delete from customer where display_name like ?", tag + "%");
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }

    private long user(String name, String role) {
        return jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                + "values (?, 'A', 'U', ?) returning id", Long.class, tag + name + "@cu.it", role);
    }

    private static UsernamePasswordAuthenticationToken as(long id, Role role) {
        return new UsernamePasswordAuthenticationToken(
                new StaffPrincipal(id, "u" + id + "@cu.it", "A U", role, Set.of()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String json(String name, String phone, String creditLimit, Long version) {
        return "{\"type\":\"BUSINESS\",\"displayName\":\"%s\",\"phone\":\"%s\",\"paymentTerms\":\"NET_30\"%s%s}".formatted(
                tag + name, phone, creditLimit == null ? "" : ",\"creditLimit\":" + creditLimit,
                version == null ? "" : ",\"version\":" + version);
    }

    private ResultActions create(long uid, Role role, String body) throws Exception {
        return mvc.perform(post("/api/customers").with(authentication(as(uid, role))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createdId(long uid, Role role, String name) throws Exception {
        String res = create(uid, role, json(name, "111", null, null)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private ResultActions addContact(long uid, Role role, long cid, String email) throws Exception {
        return mvc.perform(post("/api/customers/" + cid + "/contacts").with(authentication(as(uid, role)))
                .with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cee\",\"email\":\"" + email + "\",\"portalEnabled\":true}"));
    }

    @Test
    void salesCreateAssignsSelf() throws Exception {
        long rep = user("rep", "SALES");
        create(rep, Role.SALES, json("self", "111", "5000", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedRepId").value(rep))
                .andExpect(jsonPath("$.creditLimit").doesNotExist()); // ignored on create for SALES
    }

    @Test
    void salesCannotChangeCreditLimit() throws Exception {
        long rep = user("rep", "SALES");
        long id = createdId(rep, Role.SALES, "cl");
        mvc.perform(put("/api/customers/" + id).with(authentication(as(rep, Role.SALES))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(json("cl", "111", "9999", 0L)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminReassignmentAudited() throws Exception {
        long admin = user("adm", "ADMIN"), rep = user("rep", "SALES");
        long id = createdId(admin, Role.ADMIN, "as");
        mvc.perform(put("/api/customers/" + id + "/assignment").with(authentication(as(admin, Role.ADMIN)))
                .with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content("{\"repId\":" + rep + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedRepId").value(rep));
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type='CUSTOMER' "
                + "and action='ASSIGNMENT_CHANGED' and entity_id=?", Integer.class, id)).isEqualTo(1);
        mvc.perform(put("/api/customers/" + id + "/assignment").with(authentication(as(admin, Role.ADMIN)))
                .with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content("{\"repId\":" + admin + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateContactEmailRejected() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "dup");
        String email = tag.toLowerCase() + "@dup.ph";
        addContact(admin, Role.ADMIN, id, email).andExpect(status().isOk());
        addContact(admin, Role.ADMIN, id, email.toUpperCase()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
    }

    @Test
    void historyShowsFieldChanges() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "hist");
        mvc.perform(put("/api/customers/" + id).with(authentication(as(admin, Role.ADMIN))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(json("hist", "222", null, 0L)))
                .andExpect(status().isOk());
        String body = mvc.perform(get("/api/customers/" + id + "/history").with(authentication(as(admin, Role.ADMIN))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<List<String>> phone = JsonPath.read(body, "$.content[?(@.action=='UPDATED')].changes.phone");
        assertThat(phone).containsExactly(List.of("111", "222"));
    }

    @Test
    void staleVersionOnConcurrentEdit() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "stale");
        var edit = put("/api/customers/" + id).with(authentication(as(admin, Role.ADMIN))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(json("stale", "222", null, 0L));
        mvc.perform(edit).andExpect(status().isOk());
        mvc.perform(edit).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void lookupNeedsTwoChars() throws Exception {
        long admin = user("adm", "ADMIN");
        mvc.perform(get("/api/customers/lookup").param("q", "a").with(authentication(as(admin, Role.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    private long contactId(ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.id")).longValue();
    }

    private ResultActions postTo(long uid, Role role, String url) throws Exception {
        return mvc.perform(post(url).with(authentication(as(uid, role))).with(csrf(mvc)));
    }

    @Test
    void customerEmailNormalised() throws Exception {
        long admin = user("adm", "ADMIN");
        String body = json("em", "1", null, null).replace("\"phone\"", "\"email\":\"  Foo@Bar.PH \",\"phone\"");
        create(admin, Role.ADMIN, body).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("foo@bar.ph"));
    }

    @Test
    void concurrentDuplicateContactEmailNever500() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "race");
        for (int round = 0; round < 3; round++) {
            String email = tag.toLowerCase() + "r" + round + "@race.ph";
            var pool = Executors.newFixedThreadPool(4);
            var start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                var req = post("/api/customers/" + id + "/contacts").with(authentication(as(admin, Role.ADMIN)))
                        .with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"C\",\"email\":\"" + email + "\",\"portalEnabled\":true}");
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(req).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (var f : results) {
                codes.add(f.get());
            }
            pool.shutdown();
            assertThat(codes).containsOnly(200, 409).containsOnlyOnce(200);
        }
    }

    @Test
    void adminArchivesCustomer() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "arch");
        addContact(admin, Role.ADMIN, id, tag.toLowerCase() + "@arch.ph").andExpect(status().isOk());
        postTo(admin, Role.ADMIN, "/api/customers/" + id + "/archive").andExpect(status().isNoContent());
        var me = authentication(as(admin, Role.ADMIN));
        mvc.perform(get("/api/customers/" + id).with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty())
                .andExpect(jsonPath("$.contacts.length()").value(1));
        mvc.perform(put("/api/customers/" + id).with(me).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content(json("arch", "1", null, 0L))).andExpect(status().isNotFound());
    }

    @Test
    void contactUpdateAndArchive() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "ct");
        long cid = contactId(addContact(admin, Role.ADMIN, id, tag.toLowerCase() + "@ct.ph"));
        String url = "/api/customers/" + id + "/contacts/" + cid;
        mvc.perform(put(url).with(authentication(as(admin, Role.ADMIN))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\",\"email\":\"" + tag.toLowerCase()
                        + "@ct2.ph\",\"portalEnabled\":false,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.portalEnabled").value(false));
        postTo(admin, Role.ADMIN, url + "/archive").andExpect(status().isNoContent());
        mvc.perform(get("/api/customers/" + id).with(authentication(as(admin, Role.ADMIN))))
                .andExpect(jsonPath("$.contacts.length()").value(0));
    }

    @Test
    void assignNullUnassignsAndUnchangedIsNotAudited() throws Exception {
        long admin = user("adm", "ADMIN"), rep = user("rep", "SALES");
        long id = createdId(admin, Role.ADMIN, "an");
        String url = "/api/customers/" + id + "/assignment";
        var me = authentication(as(admin, Role.ADMIN));
        for (String body : List.of("{\"repId\":" + rep + "}", "{\"repId\":" + rep + "}", "{\"repId\":null}")) {
            mvc.perform(put(url).with(me).with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/customers/" + id).with(me)).andExpect(jsonPath("$.assignedRepId").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where entity_type='CUSTOMER' "
                + "and action='ASSIGNMENT_CHANGED' and entity_id=?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void salesCannotArchiveOrAssign() throws Exception {
        long rep = user("rep", "SALES");
        long id = createdId(rep, Role.SALES, "sa");
        postTo(rep, Role.SALES, "/api/customers/" + id + "/archive").andExpect(status().isForbidden());
        mvc.perform(put("/api/customers/" + id + "/assignment").with(authentication(as(rep, Role.SALES)))
                .with(csrf(mvc)).contentType(MediaType.APPLICATION_JSON).content("{\"repId\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationErrorsUseStandardShape() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "vs");
        var me = authentication(as(admin, Role.ADMIN));
        mvc.perform(get("/api/customers/lookup").param("q", "a").with(me)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("q"));
        mvc.perform(put("/api/customers/" + id + "/assignment").with(me).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"repId\":" + admin + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("repId"));
    }

    @Test
    void taxTreatmentDefaultsToRegular() throws Exception {
        long admin = user("adm", "ADMIN");
        create(admin, Role.ADMIN, json("tt", "111", null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.taxTreatment").value("REGULAR"));
    }

    @Test
    void taxTreatmentChangeAudited() throws Exception {
        long rep = user("rep", "SALES");
        long id = createdId(rep, Role.SALES, "ttc");
        String body = json("ttc", "111", null, 0L).replace("}", ",\"taxTreatment\":\"ZERO_RATED\"}");
        mvc.perform(put("/api/customers/" + id).with(authentication(as(rep, Role.SALES))).with(csrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taxTreatment").value("ZERO_RATED"));
        String hist = mvc.perform(get("/api/customers/" + id + "/history").with(authentication(as(rep, Role.SALES))))
                .andReturn().getResponse().getContentAsString();
        List<List<String>> tt = JsonPath.read(hist, "$.content[?(@.action=='UPDATED')].changes.taxTreatment");
        assertThat(tt).containsExactly(List.of("REGULAR", "ZERO_RATED"));
    }

    @Test
    void visibleIdsForSalesOnlyOwnIncludingArchived() throws Exception {
        long rep = user("rep", "SALES"), other = user("oth", "SALES");
        long own = createdId(rep, Role.SALES, "v1");
        long archived = createdId(rep, Role.SALES, "v2");
        long foreign = createdId(other, Role.SALES, "v3");
        jdbc.update("update customer set archived_at = now() where id = ?", archived);
        var ids = customers.visibleCustomerIds(new ph.thecoffeejunkie.crm.shared.Scope(rep, Role.SALES, Set.of()));
        assertThat(ids).hasValueSatisfying(set -> assertThat(set).contains(own, archived).doesNotContain(foreign));
        assertThat(customers.visibleCustomerIds(new ph.thecoffeejunkie.crm.shared.Scope(rep, Role.WAREHOUSE, Set.of())))
                .contains(Set.of());
    }

    @Test
    void visibleIdsForAdminUnrestricted() {
        long admin = user("adm", "ADMIN");
        assertThat(customers.visibleCustomerIds(new ph.thecoffeejunkie.crm.shared.Scope(admin, Role.ADMIN, Set.of())))
                .isEmpty();
    }

    @Test
    void portalContactEmailsSkipsDisabledAndArchived() throws Exception {
        long admin = user("adm", "ADMIN");
        long id = createdId(admin, Role.ADMIN, "pce");
        String t = tag.toLowerCase();
        addContact(admin, Role.ADMIN, id, t + "a@pce.ph").andExpect(status().isOk());
        addContact(admin, Role.ADMIN, id, t + "b@pce.ph").andExpect(status().isOk());
        addContact(admin, Role.ADMIN, id, t + "c@pce.ph").andExpect(status().isOk());
        jdbc.update("update customer_contact set portal_enabled = false where email = ?", t + "b@pce.ph");
        jdbc.update("update customer_contact set archived_at = now() where email = ?", t + "c@pce.ph");
        assertThat(customers.portalContactEmails(id)).containsExactly(t + "a@pce.ph");
    }

    @Test
    void socialMediaSavedSearchableAndCapped() throws Exception {
        long rep = user("rep", "SALES");
        String handle = tag + "-ana.c";
        create(rep, Role.SALES, json("sm", "111", null, null).replace("}",
                ",\"socialMedia\":\"facebook.com/" + handle + "\\ninstagram.com/anac\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialMedia").value("facebook.com/" + handle + "\ninstagram.com/anac"));
        mvc.perform(get("/api/customers").param("q", handle).with(authentication(as(rep, Role.SALES))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value(tag + "sm"));
        create(rep, Role.SALES, json("sm2", "111", null, null).replace("}", ",\"socialMedia\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
