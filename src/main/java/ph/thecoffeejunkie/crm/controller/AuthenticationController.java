package ph.thecoffeejunkie.crm.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.dto.request.AuthenticationRequest;
import ph.thecoffeejunkie.crm.service.AuthenticationService;
import ph.thecoffeejunkie.crm.service.RegistrationService;
import ph.thecoffeejunkie.crm.util.JwtUtil;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final RegistrationService registrationService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody AuthenticationRequest authenticationRequest) {
        var jwt = authenticationService.authenticate(authenticationRequest);

        authenticationService.addJwtToCookie(jwt);
        return ResponseEntity.ok(jwt);
    }

    @PostMapping("/check-token")
    public ResponseEntity<Boolean> checkToken(HttpServletRequest request) {
        var token = jwtUtil.extractTokenFromCookies(request);

        if (token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Never log the token itself - it is a live credential.
        log.debug("Checking session token");
        return ResponseEntity.ok(authenticationService.isTokenValid(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        try {
            String token = jwtUtil.extractTokenFromRequest(request);
            if (!token.isEmpty()) {
                authenticationService.revoke(token);
            }
        } catch (RuntimeException e) {
            // Still sign the browser out; the token itself stays valid until it expires.
            log.error("Could not revoke token on logout; clearing the cookie only", e);
        } finally {
            authenticationService.clearJwtCookie();
        }
        return ResponseEntity.ok().build();
    }

    /** Signs the current user out on every device (e.g. after a lost laptop). */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        try {
            authenticationService.revokeAllFor(authentication.getName());
        } catch (RuntimeException e) {
            // The other devices are NOT signed out - say so instead of pretending it worked.
            log.error("Could not sign {} out everywhere", authentication.getName(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } finally {
            authenticationService.clearJwtCookie();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register")
    public String register(@RequestBody AuthenticationRequest authenticationRequest) {
        registrationService.register(authenticationRequest);
        return "User registered successfully";
    }
}
