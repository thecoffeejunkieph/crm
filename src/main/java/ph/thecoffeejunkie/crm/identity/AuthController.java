package ph.thecoffeejunkie.crm.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private static final Set<String> CLIENT_TYPES = Set.of("WEB", HeaderOrCookieSessionIdResolver.ANDROID);

    private final LoginService loginService;

    AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    /** The SPA calls this first; reading the token makes the XSRF-TOKEN cookie get written. */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void csrf(CsrfToken token) {
        token.getToken();
    }

    /** The session is created only after login's transaction commits, so a failed login never leaves one. */
    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                        HttpServletResponse response) {
        String clientType = request.getHeader(HeaderOrCookieSessionIdResolver.CLIENT_TYPE_HEADER);
        if (clientType != null && !CLIENT_TYPES.contains(clientType)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "X-Client-Type must be WEB or ANDROID",
                    Map.of("field", "X-Client-Type"));
        }
        LoginService.Outcome outcome = loginService.login(req, request);
        Principals.establish(request, response, outcome.authentication());
        String token = HeaderOrCookieSessionIdResolver.ANDROID.equals(clientType)
                ? request.getSession().getId() : null;
        return new LoginResponse(outcome.next(), token, null);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
