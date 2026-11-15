package ph.thecoffeejunkie.crm.sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ph.thecoffeejunkie.crm.shared.HtmlMail;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Sends the quote mail off-thread after commit, so a rolled-back send never mails anyone. */
@Component
class SalesMailer {

    private static final Logger log = LoggerFactory.getLogger(SalesMailer.class);

    private final HtmlMail mail;
    private final TaskExecutor executor;
    private final String frontendUrl;

    SalesMailer(HtmlMail mail, @Qualifier("applicationTaskExecutor") TaskExecutor executor,
                @Value("${app.frontend-url}") String frontendUrl) {
        this.mail = mail;
        this.executor = executor;
        this.frontendUrl = frontendUrl;
    }

    void quoteAfterCommit(List<String> to, String number, LocalDate validUntil, byte[] pdf) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> to.forEach(address -> {
                    try {
                        mail.send(address, "Quotation " + number + " from " + mail.company(), "quote-sent",
                                Map.of("number", number, "validUntil", validUntil.toString(),
                                        "link", frontendUrl + "/portal/signin"),
                                List.of(new HtmlMail.Attachment(number + ".pdf", "application/pdf", pdf)));
                    } catch (RuntimeException e) {
                        log.warn("Quote {} mail to {} failed", number, address, e);
                    }
                }));
            }
        });
    }

    /** pdf runs on the executor after commit (its own read), so rendering never holds the checkout's locks. */
    void invoiceAfterCommit(List<String> to, String number, Supplier<byte[]> pdf) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> {
                    byte[] bytes;
                    try {
                        bytes = pdf.get();
                    } catch (RuntimeException e) {
                        log.warn("Invoice {} PDF for mail failed", number, e);
                        return;
                    }
                    to.forEach(address -> {
                        try {
                            mail.send(address, "Invoice " + number + " from " + mail.company(), "invoice-sent",
                                    Map.of("number", number),
                                    List.of(new HtmlMail.Attachment(number + ".pdf", "application/pdf", bytes)));
                        } catch (RuntimeException e) {
                            log.warn("Invoice {} mail to {} failed", number, address, e);
                        }
                    });
                });
            }
        });
    }
}
