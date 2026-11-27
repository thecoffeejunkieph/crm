package ph.thecoffeejunkie.crm.purchasing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ph.thecoffeejunkie.crm.shared.HtmlMail;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Mails the PO to the supplier off-thread after commit, so a rolled-back send never mails anyone. */
@Component
class PurchasingMailer {

    private static final Logger log = LoggerFactory.getLogger(PurchasingMailer.class);

    private final HtmlMail mail;
    private final TaskExecutor executor;

    PurchasingMailer(HtmlMail mail, @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.mail = mail;
        this.executor = executor;
    }

    /** pdf runs on the executor after commit (its own read), so rendering never holds the PO lock. */
    void poAfterCommit(List<String> to, String number, String supplierName, Supplier<byte[]> pdf) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> {
                    byte[] bytes;
                    try {
                        bytes = pdf.get();
                    } catch (RuntimeException e) {
                        log.warn("PO {} PDF for mail failed", number, e);
                        return;
                    }
                    to.forEach(address -> {
                        try {
                            mail.send(address, "Purchase order " + number + " from " + mail.company(), "po-sent",
                                    Map.of("number", number, "supplier", supplierName),
                                    List.of(new HtmlMail.Attachment(number + ".pdf", "application/pdf", bytes)));
                        } catch (RuntimeException e) {
                            log.warn("PO {} mail to {} failed", number, address, e);
                        }
                    });
                });
            }
        });
    }
}
