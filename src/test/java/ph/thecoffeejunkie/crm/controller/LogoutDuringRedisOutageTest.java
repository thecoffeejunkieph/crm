package ph.thecoffeejunkie.crm.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ph.thecoffeejunkie.crm.RedisTestSupport;
import ph.thecoffeejunkie.crm.service.AuthenticationService;
import ph.thecoffeejunkie.crm.service.RegistrationService;
import ph.thecoffeejunkie.crm.service.TokenRevocationService;
import ph.thecoffeejunkie.crm.service.UserDetailsServiceImpl;
import ph.thecoffeejunkie.crm.util.JwtUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class LogoutDuringRedisOutageTest {

    private final JwtUtil jwtUtil = new JwtUtil();
    private final MockMvc mvc;

    LogoutDuringRedisOutageTest() {
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "test-secret-key-that-is-long-enough-for-hs256!!");
        var auth = new AuthenticationService(mock(AuthenticationManager.class), jwtUtil,
                mock(UserDetailsServiceImpl.class), new TokenRevocationService(RedisTestSupport.deadTemplate()));
        mvc = MockMvcBuilders.standaloneSetup(
                new AuthenticationController(auth, mock(RegistrationService.class), jwtUtil)).build();
    }

    @Test
    void logoutStillClearsTheCookieWhenRevocationFails() throws Exception {
        String token = jwtUtil.generateToken(new User("a@b.ph", "x", List.of()));

        MvcResult result = mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("jwt", token))).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertCookieCleared(result);
    }

    @Test
    void logoutAllReports503AndClearsTheCookieWhenRevocationFails() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout-all")
                .principal(new UsernamePasswordAuthenticationToken("a@b.ph", null, List.of()))).andReturn();

        assertEquals(503, result.getResponse().getStatus());
        assertCookieCleared(result);
    }

    private static void assertCookieCleared(MvcResult result) {
        String setCookie = String.join(";", result.getResponse().getHeaders("Set-Cookie"));
        assertTrue(setCookie.contains("jwt=") && setCookie.contains("Max-Age=0"), "Set-Cookie was: " + setCookie);
    }
}
