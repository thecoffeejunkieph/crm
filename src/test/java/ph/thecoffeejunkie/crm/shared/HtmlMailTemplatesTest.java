package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ph.thecoffeejunkie.crm.fulfilment.DeliveryMailView.Line;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Renders every mail template with sample data; previews land in build/mail-preview/ for a look in a browser. */
class HtmlMailTemplatesTest {

    private static final String LINK = "https://crm.example.test/portal/signin";
    private static final List<Line> LINES = List.of(
            new Line("Arabica Espresso Blend 1kg", new BigDecimal("12.000"), "BAG"),
            new Line("Paper cups 12oz (sleeve of 50)", new BigDecimal("4"), "PACK"));

    private final HtmlMail mail = new HtmlMail(null, "sales@example.test", "The Coffee Junkie", false, "https://api.example.test");

    static Stream<Object[]> templates() {
        return Stream.of(
                new Object[]{"invite", Map.of("name", "Maria", "link", LINK)},
                new Object[]{"password-reset", Map.of("link", LINK)},
                new Object[]{"portal-login", Map.of("link", LINK)},
                new Object[]{"quote-sent", Map.of("number", "SO-26-00012", "validUntil", "2026-12-15", "link", LINK)},
                new Object[]{"invoice-sent", Map.of("number", "SI-00031")},
                new Object[]{"po-sent", Map.of("number", "PO-00007", "supplier", "Bean Traders Inc.")},
                new Object[]{"order-confirmed", Map.of("number", "SO-00012", "total", "12,480.00", "link", LINK)},
                new Object[]{"payment-received", Map.of("number", "SO-00012", "amount", "5000.00",
                        "method", "BANK_TRANSFER", "receivedOn", "2026-11-28", "balance", "7480.00", "link", LINK)},
                new Object[]{"payment-rejected", Map.of("number", "SO-00012", "amount", "5000.00",
                        "reason", "No such transfer <x> on our statement", "link", LINK)},
                new Object[]{"order-dispatched", Map.of("orderNumber", "SO-00012", "number", "DO-00044",
                        "shipTo", "Unit 316, GD Plaza Novaliches, Quezon City", "lines", LINES, "link", LINK)},
                new Object[]{"order-delivered", Map.of("orderNumber", "SO-00012", "number", "DO-00044",
                        "receiverName", "Juan Dela Cruz", "deliveredAt", "Nov 28, 2026 2:15 PM", "lines", LINES,
                        "pickup", false, "photosLeftOut", 1, "link", LINK)},
                new Object[]{"overdue-reminder", Map.of("items", List.of(
                        Map.of("number", "SO-00009", "dueDate", "2026-10-30", "balance", "3200.00", "days", 29),
                        Map.of("number", "SO-00011", "dueDate", "2026-11-14", "balance", "1850.50", "days", 14)),
                        "total", "5050.50", "link", LINK)});
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templates")
    void renders(String template, Map<String, Object> vars) throws Exception {
        String html = mail.render(template, vars);
        Files.createDirectories(Path.of("build/mail-preview"));
        Files.writeString(Path.of("build/mail-preview", template + ".html"), html);

        assertThat(html).contains("src=\"https://api.example.test/tcj-logo-email.png\"").contains("The Coffee Junkie").doesNotContain(" th:").doesNotContain("class=\"")
                .doesNotContain("var(--").doesNotContain("-inline").doesNotContain("<x>");
        // MailSupport.link takes the first href as the call to action, so a template has at most one.
        assertThat(html.split("href=\"", -1)).hasSize(vars.containsKey("link") ? 2 : 1);
        if (vars.containsKey("link")) {
            assertThat(html).contains("href=\"" + LINK + "\"");
        }
    }
}
