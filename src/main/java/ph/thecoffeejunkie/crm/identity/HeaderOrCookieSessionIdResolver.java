package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.List;

/** Browsers carry the session in the TCJSESSION cookie; the Android app in the X-Auth-Token header. */
class HeaderOrCookieSessionIdResolver implements HttpSessionIdResolver {

    static final String AUTH_HEADER = "X-Auth-Token";
    static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    static final String ANDROID = "ANDROID";

    private final HttpSessionIdResolver header = new HeaderHttpSessionIdResolver(AUTH_HEADER);
    private final CookieHttpSessionIdResolver cookie = new CookieHttpSessionIdResolver();

    HeaderOrCookieSessionIdResolver(CookieSerializer cookieSerializer) {
        cookie.setCookieSerializer(cookieSerializer);
    }

    static boolean isHeaderClient(HttpServletRequest request) {
        return request.getHeader(AUTH_HEADER) != null || ANDROID.equals(request.getHeader(CLIENT_TYPE_HEADER));
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        return request.getHeader(AUTH_HEADER) != null
                ? header.resolveSessionIds(request) : cookie.resolveSessionIds(request);
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        (isHeaderClient(request) ? header : cookie).setSessionId(request, response, sessionId);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        (isHeaderClient(request) ? header : cookie).expireSession(request, response);
    }
}
