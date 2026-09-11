package ph.thecoffeejunkie.crm.filter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import ph.thecoffeejunkie.crm.util.JwtUtil;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/check-token")
                // Browsers fetch this automatically for any tab pointed at this origin (e.g.
                // someone opening an invoice PDF URL directly) - it's not a real client of the
                // API, so it shouldn't go through JWT parsing at all. Without this, an anonymous
                // request here throws on parsing an empty token and logs a WARN that reads like
                // a security event for what is completely routine browser behavior.
                || path.equals("/favicon.ico")
                // Customer-facing links reached from emails without any CRM session (quotation
                // accept/reject, invoice proof-of-payment upload). They carry their own signed
                // token as a query param and are permitAll in SecurityConfig; a real customer
                // never has our session jwt, so leaving these unexcluded meant every visit threw
                // on an empty token and logged a spurious "invalid JWT" WARN.
                || PATH_MATCHER.match("/api/v1/quotations/*/respond", path)
                || PATH_MATCHER.match("/api/v1/invoices/*/proof-of-payment", path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // This filter runs ahead of the DispatcherServlet, so JWT parsing/lookup failures
        // never reach GlobalExceptionHandler - they must be handled here instead, otherwise
        // an expired/malformed token would surface as an unhandled 500.
        try {
            String authorizationHeader = request.getHeader("Authorization");

            String username;
            String token;

            if (Objects.nonNull(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
                token = authorizationHeader.substring(7);
                username = jwtUtil.extractUsername(token);
            } else {
                token = jwtUtil.extractTokenFromCookies(request);
                username = jwtUtil.extractUsername(token);
            }

            if (Objects.nonNull(username) && Objects.isNull(SecurityContextHolder.getContext().getAuthentication())) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(token, userDetails)) {

                    UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    usernamePasswordAuthenticationToken
                            .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);

                }
            }
        } catch (JwtException | UsernameNotFoundException | IllegalArgumentException ex) {
            log.warn("Rejected request with invalid JWT on {}: {}", request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
