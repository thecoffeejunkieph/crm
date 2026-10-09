package ph.thecoffeejunkie.crm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ph.thecoffeejunkie.crm.controller.ProductController;
import ph.thecoffeejunkie.crm.service.ProductService;
import ph.thecoffeejunkie.crm.service.RateLimiter;
import ph.thecoffeejunkie.crm.service.TokenRevocationService;
import ph.thecoffeejunkie.crm.service.UserDetailsServiceImpl;
import ph.thecoffeejunkie.crm.util.JwtUtil;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP Basic would let anyone guess passwords against any endpoint, bypassing the login rate limit.
 * Only the jwt cookie / Bearer token may authenticate.
 */
@WebMvcTest(controllers = ProductController.class)
@Import(SecurityConfig.class)
class BasicAuthDisabledTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean ProductService productService;
    @MockitoBean UserDetailsServiceImpl userDetailsService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean TokenRevocationService tokenRevocationService;
    @MockitoBean RateLimiter rateLimiter;
    @MockitoBean StringRedisTemplate redis;

    @Test
    void correctPasswordOverHttpBasicIsRejected() throws Exception {
        when(userDetailsService.loadUserByUsername("a@b.ph"))
                .thenReturn(new User("a@b.ph", new BCryptPasswordEncoder(4).encode("right-password"), List.of()));

        mvc.perform(get("/api/v1/products?pageNumber=1").with(request -> {
                    request.addHeader("Authorization", "Basic " + java.util.Base64.getEncoder()
                            .encodeToString("a@b.ph:right-password".getBytes()));
                    return request;
                }))
                .andExpect(status().isUnauthorized());
    }
}
