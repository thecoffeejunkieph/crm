package ph.thecoffeejunkie.crm.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import ph.thecoffeejunkie.crm.entity.Customer;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.exception.InvalidRequestException;
import ph.thecoffeejunkie.crm.exception.LinkExpiredException;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;
import ph.thecoffeejunkie.crm.util.LogoAsset;
import ph.thecoffeejunkie.crm.util.QuotationResponseTokenService;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuotationCustomerAcceptTest {

    private static final long ID = 42L;

    private final QuotationResponseTokenService tokenService = new QuotationResponseTokenService();
    private final QuotationAcceptanceService acceptance = mock(QuotationAcceptanceService.class);
    private final QuotationEmailService service;

    QuotationCustomerAcceptTest() {
        ReflectionTestUtils.setField(tokenService, "secretKey", "test-quotation-secret-that-is-long-enough!!");
        ReflectionTestUtils.setField(tokenService, "tokenValidityDays", 30);

        Customer customer = new Customer();
        customer.setFirstName("Juan");
        customer.setLastName("Cruz");
        customer.setEmail("Juan@Example.com");
        Quotation quotation = new Quotation();
        quotation.setId(ID);
        quotation.setQuotationNumber("QT-0042");
        quotation.setCustomer(customer);
        quotation.setStatus("SENT");
        quotation.setQuotationItems(new ArrayList<>());

        QuotationRepository repository = mock(QuotationRepository.class);
        when(repository.findById(ID)).thenReturn(Optional.of(quotation));

        service = new QuotationEmailService(repository, mock(QuotationPdfService.class), tokenService, acceptance,
                mock(JavaMailSender.class), mock(LogoAsset.class), mock(DistributedLock.class),
                mock(NotificationService.class));
    }

    @Test
    void customerViewDoesNotRevealTheEmailItAsksFor() {
        var view = service.getForCustomer(ID, tokenService.generate(ID));

        assertFalse(view.toString().toLowerCase().contains("juan@example.com"));
    }

    @Test
    void wrongEmailDoesNotAccept() {
        assertThrows(InvalidRequestException.class,
                () -> service.acceptByCustomer(ID, tokenService.generate(ID), "someone@else.com"));
        verifyNoInteractions(acceptance);
    }

    @Test
    void tokenForAnotherQuotationDoesNotAccept() {
        assertThrows(InvalidRequestException.class,
                () -> service.acceptByCustomer(ID, tokenService.generate(7L), "juan@example.com"));
        assertThrows(LinkExpiredException.class,
                () -> service.acceptByCustomer(ID, "garbage", "juan@example.com"));
        verifyNoInteractions(acceptance);
    }

    @Test
    void matchingEmailAcceptsIgnoringCaseAndSpaces() {
        var view = service.acceptByCustomer(ID, tokenService.generate(ID), "  juan@example.COM ");

        verify(acceptance).acceptById(ID);
        assertTrue(view.quotationNumber().equals("QT-0042"));
    }
}
