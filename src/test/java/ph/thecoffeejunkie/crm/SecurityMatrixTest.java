package ph.thecoffeejunkie.crm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.Role;
import ph.thecoffeejunkie.crm.shared.StaffPrincipal;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.UUID;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Spec §4 as a table: who may call each endpoint. Written from the spec, not from the code. */
@IntegrationTest
class SecurityMatrixTest {

    enum Who { ANON, ADMIN, SALES, WAREHOUSE, CUSTOMER }

    private static final Set<Who> A = EnumSet.of(Who.ADMIN);
    private static final Set<Who> S = EnumSet.of(Who.SALES);
    private static final Set<Who> AS = EnumSet.of(Who.ADMIN, Who.SALES);
    private static final Set<Who> AW = EnumSet.of(Who.ADMIN, Who.WAREHOUSE);
    private static final Set<Who> STAFF = EnumSet.of(Who.ADMIN, Who.SALES, Who.WAREHOUSE);
    private static final Set<Who> PUBLIC = EnumSet.allOf(Who.class);
    private static final Set<Who> CUSTOMER = EnumSet.of(Who.CUSTOMER);
    /** Needs a PRE_2FA partial session, which none of the personas holds: everyone is turned away with 401. */
    private static final Set<Who> NONE = EnumSet.noneOf(Who.class);

    private static final Map<String, Set<Who>> MATRIX = Map.ofEntries(
            Map.entry("GET /api/auth/csrf", PUBLIC),
            Map.entry("POST /api/auth/login", PUBLIC),
            Map.entry("POST /api/auth/logout", PUBLIC),
            Map.entry("POST /api/auth/invites/accept", PUBLIC),
            Map.entry("POST /api/auth/password/forgot", PUBLIC),
            Map.entry("POST /api/auth/password/reset", PUBLIC),
            Map.entry("POST /api/auth/totp", NONE),
            // springdoc rows exist and are public in dev/test only; DocsDisabledTest covers the other profiles.
            Map.entry("GET /swagger-ui.html", PUBLIC),
            Map.entry("GET /v3/api-docs", PUBLIC),
            Map.entry("GET /v3/api-docs.yaml", PUBLIC),
            Map.entry("GET /v3/api-docs/swagger-config", PUBLIC),

            Map.entry("POST /api/portal/auth/request-link", PUBLIC),
            Map.entry("POST /api/portal/auth/redeem", PUBLIC),
            Map.entry("POST /api/portal/auth/logout", PUBLIC),
            Map.entry("GET /api/portal/me", CUSTOMER),
            Map.entry("GET /api/portal/orders", CUSTOMER),
            Map.entry("GET /api/portal/orders/{id}", CUSTOMER),
            Map.entry("POST /api/portal/orders/{id}/accept", CUSTOMER),
            Map.entry("POST /api/portal/orders/{id}/decline", CUSTOMER),
            Map.entry("GET /api/portal/orders/{id}/payments", CUSTOMER),
            Map.entry("POST /api/portal/orders/{id}/payments", CUSTOMER),
            Map.entry("PUT /api/portal/payments/{id}/form-2307", CUSTOMER),
            Map.entry("GET /api/portal/orders/{id}/quote.pdf", CUSTOMER),
            Map.entry("GET /api/portal/invoices/{id}.pdf", CUSTOMER),

            Map.entry("GET /api/me", STAFF),
            Map.entry("PUT /api/me", STAFF),
            Map.entry("POST /api/me/password", STAFF),
            Map.entry("GET /api/me/sessions", STAFF),
            Map.entry("DELETE /api/me/sessions", STAFF),
            Map.entry("DELETE /api/me/sessions/{hash}", STAFF),
            Map.entry("POST /api/me/totp/setup", STAFF),
            Map.entry("POST /api/me/totp/enable", STAFF),
            // Admins may not turn 2FA off (service rule, answered 403).
            Map.entry("DELETE /api/me/totp", EnumSet.of(Who.SALES, Who.WAREHOUSE)),

            Map.entry("GET /api/admin/users", A),
            Map.entry("POST /api/admin/users", A),
            Map.entry("PUT /api/admin/users/{id}", A),
            Map.entry("POST /api/admin/users/{id}/archive", A),
            Map.entry("POST /api/admin/users/{id}/resend-invite", A),
            Map.entry("POST /api/admin/users/{id}/totp-reset", A),
            Map.entry("GET /api/admin/users/{id}/sessions", A),
            Map.entry("DELETE /api/admin/users/{id}/sessions", A),
            Map.entry("DELETE /api/admin/users/{id}/sessions/{hash}", A),
            Map.entry("GET /api/admin/settings", A),
            Map.entry("PUT /api/admin/settings", A),
            Map.entry("GET /api/admin/activity", A),

            Map.entry("GET /api/warehouses", STAFF),
            Map.entry("GET /api/warehouses/{id}", STAFF),
            Map.entry("POST /api/warehouses", A),
            Map.entry("PUT /api/warehouses/{id}", A),
            Map.entry("POST /api/warehouses/{id}/archive", A),

            Map.entry("GET /api/products", STAFF),
            Map.entry("GET /api/products/{id}", STAFF),
            Map.entry("POST /api/products", A),
            Map.entry("PUT /api/products/{id}", A),
            Map.entry("POST /api/products/{id}/archive", A),

            Map.entry("GET /api/products/{id}/availability", AS),
            Map.entry("GET /api/stock", STAFF),
            Map.entry("GET /api/stock/{productId}/movements", STAFF),
            Map.entry("POST /api/stock/receipts", AW),
            Map.entry("GET /api/stock/receipts", AW),
            Map.entry("GET /api/stock/receipts/{id}", AW),
            Map.entry("POST /api/stock/adjustments", AW),
            Map.entry("GET /api/stock/adjustments", AW),
            Map.entry("GET /api/transfers", STAFF),
            Map.entry("GET /api/transfers/{id}", STAFF),
            Map.entry("POST /api/transfers", AW),
            Map.entry("PUT /api/transfers/{id}", AW),
            Map.entry("POST /api/transfers/{id}/dispatch", AW),
            Map.entry("POST /api/transfers/{id}/receive", AW),
            Map.entry("POST /api/transfers/{id}/cancel", AW),
            Map.entry("GET /api/delivery-orders", STAFF),
            Map.entry("GET /api/delivery-orders/{id}", STAFF),
            Map.entry("POST /api/delivery-orders/{id}/pick", AW),
            Map.entry("POST /api/delivery-orders/{id}/dispatch", AW),
            Map.entry("POST /api/delivery-orders/{id}/deliver", AW),
            Map.entry("GET /api/scan/{code}", STAFF),
            Map.entry("GET /api/labels", AW),

            Map.entry("GET /api/customers", AS),
            Map.entry("GET /api/customers/lookup", AS),
            Map.entry("GET /api/customers/{id}", AS),
            Map.entry("POST /api/customers", AS),
            Map.entry("PUT /api/customers/{id}", AS),
            Map.entry("POST /api/customers/{id}/archive", A),
            Map.entry("PUT /api/customers/{id}/assignment", A),
            Map.entry("POST /api/customers/{id}/contacts", AS),
            Map.entry("PUT /api/customers/{id}/contacts/{contactId}", AS),
            Map.entry("POST /api/customers/{id}/contacts/{contactId}/archive", AS),
            Map.entry("GET /api/customers/{id}/history", AS),
            Map.entry("GET /api/customers/{id}/orders", AS),
            Map.entry("GET /api/customers/{id}/account", AS),
            Map.entry("GET /api/customers/{id}/invoices", AS),
            Map.entry("GET /api/insights/sales", S),
            Map.entry("GET /api/insights/admin", A),
            Map.entry("GET /api/insights/warehouse", AW),
            Map.entry("GET /api/insights/targets", A),
            Map.entry("PUT /api/insights/targets/{repId}/{month}", A),
            Map.entry("POST /api/insights/targets/copy", A),
            Map.entry("GET /api/follow-ups", AS),
            Map.entry("GET /api/follow-ups/counts", AS),
            Map.entry("POST /api/follow-ups", AS),
            Map.entry("GET /api/reports/missing-2307", AS),
            Map.entry("GET /api/reports/missing-2307.csv", AS),

            Map.entry("GET /api/orders", AS),
            Map.entry("GET /api/pos/products", AS),
            Map.entry("POST /api/pos/sales", AS),
            Map.entry("POST /api/orders", AS),
            Map.entry("GET /api/orders/{id}", AS),
            Map.entry("PUT /api/orders/{id}", AS),
            Map.entry("GET /api/orders/{id}/history", AS),
            Map.entry("POST /api/orders/{id}/send-quote", AS),
            Map.entry("POST /api/orders/{id}/confirm", AS),
            Map.entry("POST /api/orders/{id}/release", AS),
            Map.entry("POST /api/orders/{id}/cover-shortage", AS),
            Map.entry("POST /api/orders/{id}/cancel", AS),
            Map.entry("GET /api/orders/{id}/revisions", AS),
            Map.entry("POST /api/orders/{id}/invoice", AS),
            Map.entry("POST /api/orders/{id}/invoice/reissue", AS),
            Map.entry("GET /api/orders/{id}/invoices", AS),
            Map.entry("GET /api/invoices/{id}.pdf", AS),
            Map.entry("GET /api/orders/{id}/payments", AS),
            Map.entry("POST /api/orders/{id}/payments", AS),
            Map.entry("POST /api/payments/{id}/verify", AS),
            Map.entry("POST /api/payments/{id}/reject", AS),
            Map.entry("PUT /api/payments/{id}/form-2307", AS),
            Map.entry("PUT /api/payments/{id}/proof", AS),
            Map.entry("GET /api/orders/{id}/quote.pdf", AS),

            Map.entry("GET /api/approvals", STAFF),
            Map.entry("POST /api/approvals", STAFF),
            Map.entry("POST /api/approvals/{id}/decision", A),
            Map.entry("POST /api/approvals/{id}/cancel", STAFF),

            Map.entry("GET /api/notifications", STAFF),
            Map.entry("GET /api/notifications/unread-count", STAFF),
            Map.entry("POST /api/notifications/{id}/read", STAFF),
            Map.entry("POST /api/notifications/read-all", STAFF),
            Map.entry("GET /api/notifications/stream", STAFF));

    // Personas are real rows: /api/me and the portal session guard look the principal up.
    private final String tag = "sm" + UUID.randomUUID().toString().substring(0, 8);
    private final Map<Who, Object> principals = new EnumMap<>(Who.class);
    private long customerId;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void seedPersonas() throws Exception {
        for (Role role : Role.values()) {
            String email = tag + role.name().toLowerCase() + "@matrix.it";
            long id = jdbc.queryForObject("insert into app_user (email, first_name, last_name, role) "
                    + "values (?, 'M', 'X', ?) returning id", Long.class, email, role.name());
            principals.put(Who.valueOf(role.name()), new StaffPrincipal(id, email, "M X", role, Set.of()));
        }
        customerId = jdbc.queryForObject("insert into customer (type, display_name, phone, payment_terms) "
                + "values ('BUSINESS', ?, '1', 'NET_30') returning id", Long.class, tag);
        String email = tag + "@matrix.ph";
        long contactId = jdbc.queryForObject("insert into customer_contact (customer_id, name, email, portal_enabled) "
                + "values (?, 'Cee', ?, true) returning id", Long.class, customerId, email);
        principals.put(Who.CUSTOMER, new ContactPrincipal(contactId, customerId, email, "Cee"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from customer_contact where customer_id=?", customerId);
        jdbc.update("delete from customer where id=?", customerId);
        jdbc.update("delete from app_user where email like ?", tag + "%");
    }

    @Test
    void tableCoversExactlyTheMappedEndpoints() {
        Set<String> actual = mappedKeys();
        Set<String> missingFromTable = new TreeSet<>(actual);
        missingFromTable.removeAll(MATRIX.keySet());
        Set<String> missingFromApp = new TreeSet<>(MATRIX.keySet());
        missingFromApp.removeAll(actual);

        assertTrue(missingFromTable.isEmpty(), "endpoints missing from the matrix: " + missingFromTable);
        assertTrue(missingFromApp.isEmpty(), "matrix rows with no endpoint: " + missingFromApp);
    }

    @Test
    void everyEndpointEnforcesItsRow() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String key : new TreeSet<>(MATRIX.keySet())) {
            Set<Who> allowed = MATRIX.get(key);
            for (Who who : Who.values()) {
                int status = call(key, who, true);
                boolean ok;
                if (allowed.contains(who)) {
                    ok = status != 401 && status != 403 && status < 500;
                } else if (who == Who.ANON || allowed.isEmpty()) {
                    ok = status == 401;
                } else {
                    // Role denial happens before any lookup, so 404 here would mean the gate is missing.
                    ok = status == 403;
                }
                if (!ok) {
                    failures.add(key + " as " + who + " -> " + status
                            + (allowed.contains(who) ? " (should be allowed)" : " (should be denied)"));
                }
            }
        }
        assertEquals(List.of(), failures);
    }

    @Test
    void mutatingEndpointsRejectCookieSessionWithoutCsrfToken() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String key : new TreeSet<>(MATRIX.keySet())) {
            if (key.startsWith("GET ")) {
                continue;
            }
            Set<Who> allowed = MATRIX.get(key);
            Who who = allowed.stream().filter(w -> w != Who.ANON).findFirst().orElse(Who.ADMIN);
            // Cookie-style session as a role the row allows, so the 403 can only come from CSRF.
            int status = call(key, who, false);
            if (status != 403) {
                failures.add(key + " without CSRF -> " + status);
            }
        }
        assertEquals(List.of(), failures);
    }

    private int call(String key, Who who, boolean withCsrf) throws Exception {
        String[] parts = key.split(" ", 2);
        String path = parts[1].replaceAll("\\{[^}]+}", "999999999");
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.request(HttpMethod.valueOf(parts[0]), path)
                .contentType(MediaType.APPLICATION_JSON).content("{}");
        if (withCsrf) {
            req.with(CsrfSupport.csrf(mvc));
        }
        switch (who) {
            case ANON -> { }
            default -> req.with(authentication(new UsernamePasswordAuthenticationToken(principals.get(who), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + who.name())))));
        }
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    private Set<String> mappedKeys() {
        Set<String> keys = new TreeSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> {
            Set<String> patterns = Objects.requireNonNull(info.getPathPatternsCondition(),
                    "mapping without path patterns: " + info).getPatternValues();
            var methods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                if (pattern.equals("/error")) {
                    continue;
                }
                if (methods.isEmpty()) {
                    keys.add("ANY " + pattern);
                }
                methods.forEach(m -> keys.add(m.name() + " " + pattern));
            }
        });
        return keys;
    }
}
