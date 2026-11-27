package ph.thecoffeejunkie.crm.insights;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What is waiting on the caller (spec 2026-11-23-staff-today-queue). Each section: total and the oldest few. */
record TodayQueue(Section toVerify, Section toRelease, Section quoteRequests, Section followUps, Section approvals) {

    record Section(long total, List<Item> items) {
    }

    /** orderId null: a follow-up or a stock adjustment. amount null when there is no money on it. */
    record Item(long id, Long orderId, String title, String detail, BigDecimal amount, LocalDate since) {
    }
}