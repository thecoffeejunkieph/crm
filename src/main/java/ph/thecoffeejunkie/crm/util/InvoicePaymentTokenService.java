package ph.thecoffeejunkie.crm.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Signs and verifies the token embedded in the proof-of-payment upload link sent in invoice
 * emails, so a customer can submit their payment receipt without an authenticated CRM session.
 */
@Component
public class InvoicePaymentTokenService {

    private static final String INVOICE_ID_CLAIM = "invoiceId";

    @Value("${invoice.payment.secret-key}")
    private String secretKey;

    @Value("${invoice.payment.token-validity-days}")
    private int tokenValidityDays;

    public String generate(Long invoiceId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + tokenValidityDays * 24L * 60 * 60 * 1000);

        return Jwts.builder()
                .claim(INVOICE_ID_CLAIM, invoiceId)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSecretKey())
                .compact();
    }

    public Long resolveInvoiceId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get(INVOICE_ID_CLAIM, Long.class);
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }
}
