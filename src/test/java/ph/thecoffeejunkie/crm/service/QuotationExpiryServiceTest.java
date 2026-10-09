package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import ph.thecoffeejunkie.crm.RedisTestSupport;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuotationExpiryServiceTest {

    private static final String LOCK_KEY = "lock:job:quotation-expiry";

    private final StringRedisTemplate template = RedisTestSupport.template();
    private final QuotationRepository repository = mock(QuotationRepository.class);
    private final QuotationExpiryService service = new QuotationExpiryService(repository, new DistributedLock(template),
            mock(NotificationService.class));

    @BeforeEach
    @AfterEach
    void clearLock() {
        template.delete(LOCK_KEY);
    }

    @Test
    void skipsWhenAnotherInstanceHoldsTheLock() {
        new DistributedLock(template).tryLock("job:quotation-expiry", Duration.ofMinutes(1));

        service.expireOverdueQuotations();

        verifyNoInteractions(repository);
    }

    @Test
    void expiresWhenLockIsFree() {
        var quotation = new Quotation();
        quotation.setStatus("SENT");
        when(repository.findByStatusInAndExpiryDateBefore(anyList(), any())).thenReturn(List.of(quotation));

        service.expireOverdueQuotations();

        assertEquals("EXPIRED", quotation.getStatus());
        verify(repository, times(1)).saveAll(anyList());
    }
}
