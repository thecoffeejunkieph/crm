package ph.thecoffeejunkie.crm.shared;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Objects;

public final class ProblemResponses {

    private ProblemResponses() {
    }

    /**
     * Hand-written ProblemDetail JSON: servlet filters run outside MVC's message converters.
     * Only constant details are passed in; requestId is [A-Za-z0-9-] (see RequestIdFilter).
     */
    public static void write(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"" + code.status().getReasonPhrase() + "\","
                + "\"status\":" + code.status().value() + ","
                + "\"detail\":\"" + detail + "\","
                + "\"code\":\"" + code.name() + "\","
                + "\"requestId\":\"" + Objects.requireNonNullElse(MDC.get(RequestIdFilter.MDC_KEY), "") + "\"}");
    }
}
