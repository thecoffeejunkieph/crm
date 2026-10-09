package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import ph.thecoffeejunkie.crm.RedisTestSupport;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;
import ph.thecoffeejunkie.crm.util.LogoAsset;
import ph.thecoffeejunkie.crm.util.QuotationResponseTokenService;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class QuotationRespondLockTest {

    @Test
    void respondReturns409WhileAnotherResponseHoldsTheLock() {
        var tokenService = new QuotationResponseTokenService();
        ReflectionTestUtils.setField(tokenService, "secretKey", "test-quotation-secret-that-is-long-enough!!");
        ReflectionTestUtils.setField(tokenService, "tokenValidityDays", 30);
        var repository = mock(QuotationRepository.class);
        var acceptance = mock(QuotationAcceptanceService.class);
        var lock = new DistributedLock(RedisTestSupport.template());
        var service = new QuotationEmailService(repository, mock(QuotationPdfService.class), tokenService,
                acceptance, mock(JavaMailSender.class), mock(LogoAsset.class), lock);

        long id = System.nanoTime(); // unique lock name per run
        lock.tryLock("quotation-accept:" + id, Duration.ofMinutes(1));
        try {
            var result = service.respond(id, tokenService.generate(id), "ACCEPT");

            assertEquals(HttpStatus.CONFLICT, result.status());
            verifyNoInteractions(acceptance, repository);
        } finally {
            lock.unlock("quotation-accept:" + id);
        }
    }
}
