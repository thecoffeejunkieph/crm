package ph.thecoffeejunkie.crm.sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ph.thecoffeejunkie.crm.customers.Customers;
import ph.thecoffeejunkie.crm.shared.DistributedLock;
import ph.thecoffeejunkie.crm.shared.HtmlMail;
import ph.thecoffeejunkie.crm.shared.PdfFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * One overdue-reminder mail per customer on the days an open order hits 1, 7, 14 or 30 days late, then every 30 days,
 * with the statement attached. Runs once per date across instances.
 */
@Component
class OverdueReminders {

    private static final Logger log = LoggerFactory.getLogger(OverdueReminders.class);

    private final Statements statements;
    private final Customers customers;
    private final HtmlMail mail;
    private final DistributedLock lock;
    private final String link;

    OverdueReminders(Statements statements, Customers customers, HtmlMail mail, DistributedLock lock,
                     @Value("${app.frontend-url}") String frontendUrl) {
        this.statements = statements;
        this.customers = customers;
        this.mail = mail;
        this.lock = lock;
        this.link = frontendUrl + "/portal/signin";
    }

    static boolean milestone(long daysOverdue) {
        return daysOverdue == 1 || daysOverdue == 7 || daysOverdue == 14 || (daysOverdue >= 30 && daysOverdue % 30 == 0);
    }

    /** Customers mailed; 0 when this date already ran. The lock is never released so a restart can't resend. */
    int sendFor(LocalDate today) {
        if (!lock.tryLock("overdue-reminders:" + today, Duration.ofHours(23))) {
            return 0;
        }
        Map<Long, List<Statements.OpenItem>> overdue = statements.openItems(Optional.empty(), today).stream()
                .filter(i -> i.daysOverdue() > 0)
                .collect(Collectors.groupingBy(Statements.OpenItem::customerId, LinkedHashMap::new, Collectors.toList()));
        int mailed = 0;
        for (var e : overdue.entrySet()) {
            if (e.getValue().stream().noneMatch(i -> milestone(i.daysOverdue()))) {
                continue;
            }
            try {
                mailed += send(e.getKey(), e.getValue(), today) ? 1 : 0;
            } catch (RuntimeException ex) {
                log.warn("Overdue reminder for customer {} failed", e.getKey(), ex);
            }
        }
        return mailed;
    }

    private boolean send(long customerId, List<Statements.OpenItem> items, LocalDate today) {
        List<String> to = customers.portalContactEmails(customerId);
        if (to.isEmpty()) {
            return false;
        }
        BigDecimal total = items.stream().map(Statements.OpenItem::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        PdfFile pdf = statements.pdf(customerId, today);
        Map<String, Object> vars = Map.of("items", items.stream().map(i -> Map.of("number", i.orderNumber(),
                        "dueDate", i.dueDate().toString(), "balance", i.balance().toPlainString(),
                        "days", i.daysOverdue())).toList(),
                "total", total.toPlainString(), "link", link);
        List<HtmlMail.Attachment> files = List.of(new HtmlMail.Attachment(pdf.filename(), "application/pdf", pdf.bytes()));
        for (String address : to) {
            try {
                mail.send(address, "Payment reminder: " + total.toPlainString() + " overdue — " + mail.company(),
                        "overdue-reminder", vars, files);
            } catch (RuntimeException ex) {
                log.warn("Overdue reminder to {} failed", address, ex);
            }
        }
        return true;
    }
}
