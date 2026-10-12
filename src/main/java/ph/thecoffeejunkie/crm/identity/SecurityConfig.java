package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import ph.thecoffeejunkie.crm.shared.RequestIdFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Objects;

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
                    if (apiDocs) {
                        a.requestMatchers("/v3/api-docs/**").permitAll();
                    } else {
                        a.requestMatchers("/v3/api-docs/**").denyAll();
                    }
                    a.requestMatchers("/api/portal/**").hasRole("CUSTOMER")
                            .requestMatchers("/api/**").hasAnyRole("ADMIN", "SALES", "WAREHOUSE")
                            .anyRequest().permitAll();
                })
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeProblem(res, ErrorCode.UNAUTHORIZED, "Login required."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeProblem(res, ErrorCode.FORBIDDEN, "You do not have access to this resource.")))
                .addFilterAfter(new AbsoluteSessionTimeoutFilter(clock), SecurityContextHolderFilter.class)
                // A 401 must not create a session just to remember the request.
                .requestCache(c -> c.disable())
                .logout(l -> l.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .build();
    }

    /**
     * Hand-written ProblemDetail JSON: security filters run outside MVC's message converters.
     * Only constant details are passed in; requestId is [A-Za-z0-9-] (see RequestIdFilter).
     */
    static void writeProblem(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"" + code.status().getReasonPhrase() + "\","
                + "\"status\":" + code.status().value() + ","
                + "\"detail\":\"" + detail + "\","
                + "\"code\":\"" + code.name() + "\","
                + "\"requestId\":\"" + Objects.requireNonNullElse(MDC.get(RequestIdFilter.MDC_KEY), "") + "\"}");
    }
}
