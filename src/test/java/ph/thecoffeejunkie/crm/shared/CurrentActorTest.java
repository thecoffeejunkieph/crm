package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentActorTest {

    private final CurrentActor actor = new CurrentActor();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void staffScopeComesFromSecurityContext() {
        var p = new StaffPrincipal(7, "a@b.c", "Ann", Role.ADMIN, Set.of(1L));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(p, null));

        assertEquals(new Scope(7, Role.ADMIN, Set.of(1L)), actor.scope());
        assertTrue(actor.scope().isAdmin());
        assertTrue(actor.contact().isEmpty());
        assertEquals("user:7", p.getName());
    }

    @Test
    void noStaffMeansUnauthorized() {
        var c = new ContactPrincipal(3, 9, "c@d.e", "Cy");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(c, null));

        assertTrue(actor.contact().isPresent());
        assertFalse(actor.staff().isPresent());
        assertEquals(ErrorCode.UNAUTHORIZED, assertThrows(DomainException.class, actor::scope).code());
        assertEquals("contact:3", c.getName());
    }

    @Test
    void principalsSurviveJdkSerialization() throws Exception {
        var p = new StaffPrincipal(7, "a@b.c", "Ann", Role.SALES, Set.of(1L, 2L));
        var out = new ByteArrayOutputStream();
        try (var o = new ObjectOutputStream(out)) {
            o.writeObject(p);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals(p, in.readObject());
        }
    }
}
