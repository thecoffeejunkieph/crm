package ph.thecoffeejunkie.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ph.thecoffeejunkie.crm.entity.Quotation;
import ph.thecoffeejunkie.crm.repository.QuotationRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

// Persists what QuotationAcceptanceService already enforces at accept-time but never saved: a quotation past its expiryDate is EXPIRED.
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationExpiryService {

    // Mirrors DashboardService.OPEN_STATUSES - the set of statuses a quotation still
    // awaiting a customer decision can be in before it is resolved one way or another.
    private static final List<String> OPEN_STATUSES = List.of("DRAFT", "PENDING", "APPROVED", "SENT");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Manila");

    private final QuotationRepository quotationRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Manila")
    @Transactional
    public void expireOverdueQuotations() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<Quotation> overdue = quotationRepository.findByStatusInAndExpiryDateBefore(OPEN_STATUSES, today);

        if (overdue.isEmpty()) {
            return;
        }

        overdue.forEach(quotation -> quotation.setStatus("EXPIRED"));
        quotationRepository.saveAll(overdue);

        log.info("Expired {} quotation(s) past their expiry date", overdue.size());
    }
}
