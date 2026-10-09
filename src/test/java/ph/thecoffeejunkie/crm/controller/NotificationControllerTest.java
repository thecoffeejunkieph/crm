package ph.thecoffeejunkie.crm.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ph.thecoffeejunkie.crm.config.SecurityConfig;
import ph.thecoffeejunkie.crm.exception.ResourceNotFoundException;
import ph.thecoffeejunkie.crm.service.NotificationService;
import ph.thecoffeejunkie.crm.service.NotificationStreams;
import ph.thecoffeejunkie.crm.service.RateLimiter;
import ph.thecoffeejunkie.crm.service.TokenRevocationService;
import ph.thecoffeejunkie.crm.service.UserDetailsServiceImpl;
import ph.thecoffeejunkie.crm.util.JwtUtil;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean NotificationService notificationService;
    @MockitoBean NotificationStreams streams;
    @MockitoBean UserDetailsServiceImpl userDetailsService;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean TokenRevocationService tokenRevocationService;
    @MockitoBean RateLimiter rateLimiter;
    @MockitoBean StringRedisTemplate redis;

    @Test
    void unreadCountReturnsCount() throws Exception {
        when(notificationService.unreadCount("me@x.ph")).thenReturn(2L);

        mvc.perform(get("/api/v1/notifications/unread-count").with(user("me@x.ph")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"count\":2}"));
    }

    @Test
    void markReadOfOtherUsersNotificationIs404() throws Exception {
        doThrow(ResourceNotFoundException.of("Notification", 5L)).when(notificationService).markRead("me@x.ph", 5L);

        mvc.perform(post("/api/v1/notifications/5/read").with(user("me@x.ph")))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamOpensForCurrentUser() throws Exception {
        when(notificationService.unreadCount("me@x.ph")).thenReturn(4L);
        when(streams.open("me@x.ph", 4L)).thenReturn(new SseEmitter());

        mvc.perform(get("/api/v1/notifications/stream").with(user("me@x.ph")))
                .andExpect(request().asyncStarted());

        verify(streams).open("me@x.ph", 4L);
    }

    @Test
    void streamAsyncDispatchIsNotRejected() throws Exception {
        // Authenticate through JWTFilter like a real browser does (not with user(), which MockMvc
        // would carry into the async dispatch and hide the problem).
        when(jwtUtil.extractTokenFromRequest(any())).thenReturn("t");
        when(jwtUtil.extractUsername("t")).thenReturn("me@x.ph");
        when(jwtUtil.validateToken(eq("t"), any())).thenReturn(true);
        when(jwtUtil.extractJti("t")).thenReturn("j");
        when(jwtUtil.extractIssuedAt("t")).thenReturn(Instant.now());
        when(userDetailsService.loadUserByUsername("me@x.ph")).thenReturn(new User("me@x.ph", "x", List.of()));
        SseEmitter emitter = new SseEmitter();
        when(streams.open("me@x.ph", 0L)).thenReturn(emitter);

        MvcResult result = mvc.perform(get("/api/v1/notifications/stream").header("Authorization", "Bearer t"))
                .andExpect(request().asyncStarted())
                .andReturn();
        emitter.complete();

        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedStreamIs401() throws Exception {
        mvc.perform(get("/api/v1/notifications/stream")).andExpect(status().isUnauthorized());
    }
}
