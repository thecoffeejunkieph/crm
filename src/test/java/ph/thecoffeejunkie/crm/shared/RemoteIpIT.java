package ph.thecoffeejunkie.crm.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.filter.OncePerRequestFilter;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind Traefik, getRemoteAddr() must be the real client (audit clientIp, per-IP rate limits), and
 * X-Forwarded-For from anyone else must be ignored. RemoteIpValve is Tomcat-level, so this needs a real server.
 */
class RemoteIpIT {

    /** Same value as prod and staging (docker networks live in the private ranges). */
    static final String PRIVATE_RANGES = "10[.][0-9]{1,3}[.][0-9]{1,3}[.][0-9]{1,3}"
            + "|192[.]168[.][0-9]{1,3}[.][0-9]{1,3}"
            + "|172[.](1[6-9]|2[0-9]|3[01])[.][0-9]{1,3}[.][0-9]{1,3}"
            + "|127[.][0-9]{1,3}[.][0-9]{1,3}[.][0-9]{1,3}"
            + "|0:0:0:0:0:0:0:1|::1";

    static final AtomicReference<String> SEEN = new AtomicReference<>();

    @TestConfiguration
    static class Capture {
        @Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<OncePerRequestFilter> captureRemoteAddr() {
            var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<OncePerRequestFilter>(
                    new OncePerRequestFilter() {
                        @Override
                        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                                        FilterChain chain) throws IOException, jakarta.servlet.ServletException {
                            SEEN.set(req.getRemoteAddr());
                            chain.doFilter(req, res);
                        }
                    });
            reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return reg;
        }
    }

    static String remoteAddrSeenFor(int port) throws Exception {
        SEEN.set(null);
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/csrf"))
                .header("X-Forwarded-For", "203.0.113.9").build(), HttpResponse.BodyHandlers.discarding());
        return SEEN.get();
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "server.forward-headers-strategy=native",
            "server.tomcat.remoteip.internal-proxies=" + PRIVATE_RANGES})
    @ActiveProfiles("test")
    @ContextConfiguration(initializers = IntegrationTest.Containers.class, classes = Capture.class)
    static class TrustedProxy {
        @LocalServerPort int port;

        @Test
        void clientIpComesFromForwardedHeader() throws Exception {
            assertThat(remoteAddrSeenFor(port)).isEqualTo("203.0.113.9");
        }
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
            "server.forward-headers-strategy=native",
            "server.tomcat.remoteip.internal-proxies=10[.]99[.].*"})
    @ActiveProfiles("test")
    @ContextConfiguration(initializers = IntegrationTest.Containers.class, classes = Capture.class)
    static class UntrustedPeer {
        @LocalServerPort int port;

        @Test
        void forwardedHeaderFromUntrustedPeerIsIgnored() throws Exception {
            assertThat(remoteAddrSeenFor(port)).isEqualTo("127.0.0.1");
        }
    }

    @Test
    void proxyRegexMatchesDockerRangesOnly() {
        Pattern p = Pattern.compile(PRIVATE_RANGES);
        assertThat(p.matcher("172.18.0.5").matches()).isTrue();
        assertThat(p.matcher("10.0.0.7").matches()).isTrue();
        assertThat(p.matcher("203.0.113.9").matches()).isFalse();
        assertThat(p.matcher("172.32.0.1").matches()).isFalse();
        assertThat(p.matcher("8.8.8.8").matches()).isFalse();
    }

    @Test
    void prodAndStagingUseThatRegex() throws IOException {
        for (String env : new String[]{"prod", "staging"}) {
            String props = Files.readString(Path.of("src/main/resources/application-" + env + ".properties"));
            assertThat(props).contains("server.forward-headers-strategy=native")
                    .contains("server.tomcat.remoteip.internal-proxies=" + PRIVATE_RANGES);
        }
    }
}
