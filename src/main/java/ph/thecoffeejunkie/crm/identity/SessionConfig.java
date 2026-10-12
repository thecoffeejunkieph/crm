package ph.thecoffeejunkie.crm.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.time.Clock;

/** Sessions live in Redis (Spring Session, auto-configured); this only picks how the id travels. */
@Configuration
class SessionConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** A custom resolver means Boot no longer applies server.servlet.session.cookie.*, so it is set here. */
    @Bean
    CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.name}") String name,
                                      @Value("${app.session.cookie-secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName(name);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }

    @Bean
    HttpSessionIdResolver httpSessionIdResolver(CookieSerializer cookieSerializer) {
        return new HeaderOrCookieSessionIdResolver(cookieSerializer);
    }
}
