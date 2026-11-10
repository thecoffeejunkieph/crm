package ph.thecoffeejunkie.crm.sales;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The approval subject version of an order: a hash of its content, so an approval can be granted for a revision
 * before it exists, and any content change invalidates old approvals. 52 bits, so it survives a JS number.
 */
final class OrderFingerprint {

    private OrderFingerprint() {
    }

    static long of(SalesOrder o) {
        StringBuilder s = new StringBuilder()
                .append(o.customerId).append('|').append(o.paymentTerms).append('|').append(plain(o.shippingFee)).append('|');
        for (OrderLine l : o.lines) {
            s.append(l.productId).append('|').append(plain(l.qty)).append('|').append(plain(l.unitPrice)).append('|')
                    .append(plain(l.discountPct)).append('|');
        }
        if (o.discountValue.signum() > 0) { // only when set, so approvals on orders without one stay valid
            s.append(o.discountType).append('|').append(plain(o.discountValue)).append('|');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash, 0, 8).getLong() & 0x000F_FFFF_FFFF_FFFFL;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String plain(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }
}
