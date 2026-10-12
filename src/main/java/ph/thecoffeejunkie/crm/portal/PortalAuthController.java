package ph.thecoffeejunkie.crm.portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.identity.Principals;
import ph.thecoffeejunkie.crm.shared.ContactPrincipal;
import ph.thecoffeejunkie.crm.shared.CurrentActor;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/portal")
class PortalAuthController {

    /** Idle timeout for portal sessions; the 12h absolute limit applies to staff principals only. */
    static final Duration SESSION_TTL = Duration.ofDays(7);

    private final PortalAuthService service;
    private final CurrentActor actor;

    PortalAuthController(PortalAuthService service, CurrentActor actor) {
        this.service = service;
        this.actor = actor;
    }

    @PostMapping("/auth/request-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestLink(@Valid @RequestBody PortalLinkRequest req) {
        service.requestLink(req.email());
    }

    @PostMapping("/auth/redeem")
    PortalMeResponse redeem(@Valid @RequestBody PortalRedeemRequest req, HttpServletRequest request,
                            HttpServletResponse response) {
        ContactPrincipal c = service.redeem(req.token());
        Principals.establish(request, response, new UsernamePasswordAuthenticationToken(c, null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        request.getSession().setMaxInactiveInterval((int) SESSION_TTL.toSeconds());
        return service.me(c);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    PortalMeResponse me() {
        return service.me(actor.contact().orElseThrow(() -> new DomainException(ErrorCode.UNAUTHORIZED,
                "Login required")));
    }
}
