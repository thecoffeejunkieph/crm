package ph.thecoffeejunkie.crm.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.stereotype.Service;
import ph.thecoffeejunkie.crm.IntegrationTest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@Import(ServiceCallLoggingIT.Probes.class)
class ServiceCallLoggingIT {

    record ProbeEvent() {
    }

    @Service
    static class LoggingProbeService {
        // Static: a field read through the CGLIB proxy would be the proxy's own, empty one.
        static final Map<String, String> seen = new java.util.concurrent.ConcurrentHashMap<>();

        int add(int a, String password) {
            return a + 1;
        }

        void nap() throws InterruptedException {
            Thread.sleep(600);
        }

        void boom() {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "boom");
        }

        @EventListener
        void on(ProbeEvent e) {
            seen.put("jobId", MDC.get("jobId"));
            seen.put("requestId", MDC.get("requestId"));
        }

        @Scheduled(initialDelay = 30, fixedDelay = 30, timeUnit = TimeUnit.DAYS)
        void tick() {
            seen.put("tickJob", MDC.get("job"));
            seen.put("tickJobId", MDC.get("jobId"));
        }
    }

    @TestConfiguration
    static class Probes {
        @Bean
        LoggingProbeService loggingProbeService() {
            return new LoggingProbeService();
        }
    }

    @Autowired LoggingProbeService probe;
    @Autowired ApplicationEventPublisher events;
    @Autowired LoggingSystem logging;

    @AfterEach
    void reset() {
        logging.setLogLevel("ph.thecoffeejunkie.crm", LogLevel.DEBUG);
        MDC.clear();
        LoggingProbeService.seen.clear();
    }

    @Test
    void entryAndExitAtDebug(CapturedOutput out) {
        assertThat(probe.add(1, "hunter2")).isEqualTo(2);
        assertThat(out.getOut()).contains("→ add(a=1, password=***)").containsPattern("← add in \\d+ms")
                .doesNotContain("hunter2");
    }

    @Test
    void slowCallWarnsAtInfo(CapturedOutput out) throws Exception {
        logging.setLogLevel("ph.thecoffeejunkie.crm", LogLevel.INFO);
        probe.nap();
        assertThat(out.getOut()).containsPattern("WARN .*slow → nap in \\d+ms").doesNotContain("→ nap(");
    }

    @Test
    void exceptionLoggedAndPropagated(CapturedOutput out) {
        assertThatThrownBy(() -> probe.boom()).isInstanceOf(DomainException.class).hasMessage("boom");
        assertThat(out.getOut()).contains("✗ boom threw DomainException: boom");
    }

    @Test
    void listenerKeepsRequestIdAndAddsJobId() {
        MDC.put("requestId", "r1");
        events.publishEvent(new ProbeEvent());
        assertThat(LoggingProbeService.seen.get("jobId")).startsWith("job-");
        assertThat(LoggingProbeService.seen.get("requestId")).isEqualTo("r1");
        assertThat(MDC.get("requestId")).isEqualTo("r1");
        assertThat(MDC.get("jobId")).isNull();
    }

    @Test
    void scheduledTaskRunsWithJobIds() throws Exception {
        Runnable task = JobMdc.decorate(new ScheduledMethodRunnable(probe,
                LoggingProbeService.class.getDeclaredMethod("tick")));
        task.run();
        assertThat(LoggingProbeService.seen.get("tickJob")).isEqualTo("LoggingProbeService.tick");
        assertThat(LoggingProbeService.seen.get("tickJobId")).startsWith("job-");
        assertThat(MDC.getCopyOfContextMap() == null || MDC.getCopyOfContextMap().isEmpty()).isTrue();
    }
}
