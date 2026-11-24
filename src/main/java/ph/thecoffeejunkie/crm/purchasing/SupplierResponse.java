package ph.thecoffeejunkie.crm.purchasing;

import java.math.BigDecimal;

record SupplierResponse(long id, String code, String name, String contactName, String phone, String email,
                        String address, Integer paymentTermsDays, Integer leadTimeDays, BigDecimal creditLimit,
                        BigDecimal openingBalance, String notes, boolean archived, SupplierCredit credit,
                        long version) {
}
