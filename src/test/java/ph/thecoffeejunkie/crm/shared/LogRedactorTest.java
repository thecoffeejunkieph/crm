package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogRedactorTest {

    record Login(String email, String password, String totpCode) {
    }

    record Create(String idempotencyKey, String name) {
    }

    record Customer(String displayName, String tin) {
    }

    static class Node {
        public Node next;

        public Node getNext() {
            return next;
        }
    }

    @Test
    void masksByName() {
        String out = LogRedactor.render(new Login("juan@acme.com", "hunter2", "123456"));
        assertThat(out).contains("email=j***@acme.com").contains("password=***").contains("totpCode=***")
                .doesNotContain("hunter2").doesNotContain("123456");
    }

    @Test
    void keepsIdempotencyKey() {
        assertThat(LogRedactor.render(new Create("k-1", "Acme"))).contains("idempotencyKey=k-1").contains("name=Acme");
    }

    @Test
    void masksTin() {
        assertThat(LogRedactor.render(new Customer("Acme", "123-456-789-000"))).contains("tin=***000")
                .doesNotContain("123-456");
    }

    @Test
    void cutsLongStrings() {
        String out = LogRedactor.render("x".repeat(300));
        assertThat(out).hasSize(201).endsWith("…");
    }

    @Test
    void capsCollections() {
        assertThat(LogRedactor.render(List.of(1, 2, 3, 4, 5, 6, 7, 8))).isEqualTo("[1, 2, 3, 4, 5 (+3 more)]");
    }

    @Test
    void bytes() {
        assertThat(LogRedactor.render(new byte[1234])).isEqualTo("<1234 bytes>");
    }

    @Test
    void cycle() {
        Node n = new Node();
        n.next = n;
        assertThat(LogRedactor.render(n)).contains("<cycle>");
    }

    @Test
    void renderArgsMasksByParameterName() {
        String out = LogRedactor.renderArgs(new String[]{"email", "password", "items"},
                new Object[]{"ana@x.ph", "s3cret", new ArrayList<>(List.of("a"))});
        assertThat(out).isEqualTo("email=a***@x.ph, password=***, items=[a]");
    }

    @Test
    void nullsAndPlainValues() {
        assertThat(LogRedactor.render(null)).isEqualTo("null");
        assertThat(LogRedactor.render(42L)).isEqualTo("42");
        assertThat(LogRedactor.renderArgs(new String[]{"id"}, new Object[]{7L})).isEqualTo("id=7");
    }
}
