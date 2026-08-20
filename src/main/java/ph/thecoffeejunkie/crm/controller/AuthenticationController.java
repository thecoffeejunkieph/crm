package ph.thecoffeejunkie.crm.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

        log.info("Checking token: {}", token);
        return ResponseEntity.ok(authenticationService.isTokenValid(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authenticationService.clearJwtCookie();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register")
    public String register(@RequestBody AuthenticationRequest authenticationRequest) {
        registrationService.register(authenticationRequest);
        return "User registered successfully";
    }
}
