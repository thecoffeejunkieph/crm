package ph.thecoffeejunkie.crm.shared;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemDetailAdviceTest {

    record Body(@NotBlank String name) {}

    @RestController
    static class TestController {
        @GetMapping("/dup")
        void dup() {
            throw new DomainException(ErrorCode.DUPLICATE, "Email taken", Map.of("field", "email"));
        }

        @PostMapping("/valid")
        void valid(@Valid @RequestBody Body body) {}

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ProblemDetailAdvice())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void domainExceptionBecomesProblemDetail() throws Exception {
        mvc.perform(get("/dup"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.requestId").value(not(emptyString())))
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void validationErrorsListFields() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void malformedJsonKeeps400WithCodeAndRequestId() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value(not(emptyString())));
    }

    @Test
    void wrongMethodKeeps405WithRequestIdAndNoCode() throws Exception {
        mvc.perform(post("/dup"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.requestId").value(not(emptyString())))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void unexpectedErrorHidesDetail() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.requestId").value(not(emptyString())))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("secret"))));
    }
}
