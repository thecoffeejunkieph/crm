package ph.thecoffeejunkie.crm;

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real XSRF cookie + header. Spring Security's own {@code csrf()} post-processor swaps the shared CsrfFilter's
 * token repository for the life of the cached context, which breaks later cookie-based tests.
 */
public final class CsrfSupport {

    private CsrfSupport() {
    }

    public static RequestPostProcessor csrf(MockMvc mvc) {
        Cookie x;
        try {
            x = mvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
                    .andReturn().getResponse().getCookie("XSRF-TOKEN");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return r -> {
            var all = new ArrayList<Cookie>(r.getCookies() == null ? java.util.List.of() : Arrays.asList(r.getCookies()));
            all.add(x);
            r.setCookies(all.toArray(new Cookie[0]));
            r.addHeader("X-XSRF-TOKEN", x.getValue());
            return r;
        };
    }
}
