package ph.thecoffeejunkie.crm.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ph.thecoffeejunkie.crm.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AuditLogLoggingIT {

    @Autowired AuditLog audit;
    @Autowired PlatformTransactionManager txm;

    @Test
    void recordLogs(CapturedOutput out) {
        TransactionTemplate tx = new TransactionTemplate(txm);
        tx.executeWithoutResult(s -> {
            audit.record(AuditAction.CREATED, "CUSTOMER", 987654L, "Created Acme",
                    AuditChanges.diff().field("displayName", null, "Acme Secret Value"));
            s.setRollbackOnly();
        });
        assertThat(out.getOut()).contains("CREATED CUSTOMER 987654 \"Created Acme\" changed=[displayName]")
                .doesNotContain("Acme Secret Value");
    }

    @Test
    void emailsInSummariesAreMaskedAndFailedLoginsNameNobody(CapturedOutput out) {
        TransactionTemplate tx = new TransactionTemplate(txm);
        tx.executeWithoutResult(s -> {
            audit.record(AuditAction.INVITE_SENT, "USER", 987655L, "Invited juan.cruz@acme.com", null);
            audit.recordFor("SYSTEM", null, null, AuditAction.LOGIN_FAILED, "USER", null,
                    "Failed login for MyTyped-Passw0rd");
            s.setRollbackOnly();
        });
        assertThat(out.getOut()).contains("INVITE_SENT USER 987655 \"Invited j***@acme.com\"")
                .doesNotContain("juan.cruz@acme.com").contains("LOGIN_FAILED USER null \"Failed login\"")
                .doesNotContain("MyTyped-Passw0rd");
    }
}
