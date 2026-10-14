package ph.thecoffeejunkie.crm.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.ProblemResponses;

import java.time.Clock;

import static ph.thecoffeejunkie.crm.identity.HeaderOrCookieSessionIdResolver.ANDROID;
import static ph.thecoffeejunkie.crm.identity.HeaderOrCookieSessionIdResolver.AUTH_HEADER;
import static ph.thecoffeejunkie.crm.identity.HeaderOrCookieSessionIdResolver.CLIENT_TYPE_HEADER;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, Environment env, Clock clock,
                                    @Value("${app.csrf.cookie-domain:}") String csrfCookieDomain) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(c -> {
            if (!csrfCookieDomain.isBlank()) {
                c.domain(csrfCookieDomain);
            }
        });
        boolean apiDocs = env.acceptsProfiles(Profiles.of("dev", "test"));
        return http
                .cors(Customizer.withDefaults())
                // Header-authenticated (Android) requests carry no ambient credentials, so CSRF does not apply.
                .csrf(c -> c.spa().csrfTokenRepository(csrfRepository).ignoringRequestMatchers(
                        new RequestHeaderRequestMatcher(AUTH_HEADER),
                        new RequestHeaderRequestMatcher(CLIENT_TYPE_HEADER, ANDROID)))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout",
                                    "/api/auth/invites/accept").permitAll()
                            // /totp is reachable with PRE_2FA only; its controller checks that authority.
                            .requestMatchers("/api/auth/totp", "/api/auth/password/forgot",
                                    "/api/auth/password/reset", "/api/portal/auth/**").permitAll();
                    // "/v3/api-docs*" also catches /v3/api-docs.yaml, which "/v3/api-docs/**" does not.
                    String[] docs = {"/v3/api-docs*", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};
                    if (apiDocs) {
                        a.requestMatchers(docs).permitAll();
                    } else {
                        a.requestMatchers(docs).denyAll();
                    }
                    // Admins must reach enrolment before they hold a ROLE_ authority.
                    a.requestMatchers(HttpMethod.POST, "/api/me/totp/setup", "/api/me/totp/enable")
                            .hasAnyAuthority("ENROL_2FA", "ROLE_ADMIN", "ROLE_SALES", "ROLE_WAREHOUSE");
                    // Coarse role gates ahead of @PreAuthorize: argument validation runs before method security,
                    // so without these a forbidden caller would get a 400 instead of a 403.
                    a.requestMatchers("/api/admin/**", "/api/approvals/*/decision").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/warehouses/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PUT, "/api/warehouses/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                            .requestMatchers("/api/customers/**", "/api/orders/**", "/api/payments/**", "/api/invoices/**",
                                    "/api/reports/**").hasAnyRole("ADMIN", "SALES");
                    a.requestMatchers("/api/portal/**").hasRole("CUSTOMER")
                            .requestMatchers("/api/**").hasAnyRole("ADMIN", "SALES", "WAREHOUSE")
                            .anyRequest().permitAll();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                ProblemResponses.write(res, ErrorCode.UNAUTHORIZED, "Login required."))
                        .accessDeniedHandler((req, res, ex) ->
                                ProblemResponses.write(res, ErrorCode.FORBIDDEN, "You do not have access to this resource.")))
                .addFilterAfter(new AbsoluteSessionTimeoutFilter(clock), SecurityContextHolderFilter.class)
                // A 401 must not create a session just to remember the request.
                .requestCache(c -> c.disable())
                .logout(l -> l.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .build();
    }
}
