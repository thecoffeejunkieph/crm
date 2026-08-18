package ph.thecoffeejunkie.crm.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Signs and verifies the token embedded in the accept/reject links sent in quotation
 * emails, so a customer can act on a quotation without an authenticated CRM session.
 */
@Component
public class QuotationResponseTokenService {

    private static final String QUOTATION_ID_CLAIM = "quotationId";

    @Value("${quotation.response.secret-key}")
    private String secretKey;

    @Value("${quotation.response.token-validity-days}")
    private int tokenValidityDays;

    public String generate(Long quotationId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + tokenValidityDays * 24L * 60 * 60 * 1000);

        return Jwts.builder()
                .claim(QUOTATION_ID_CLAIM, quotationId)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSecretKey())
                .compact();
    }

    public Long resolveQuotationId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get(QUOTATION_ID_CLAIM, Long.class);
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }
}
