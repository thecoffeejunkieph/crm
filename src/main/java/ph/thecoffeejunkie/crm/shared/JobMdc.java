package ph.thecoffeejunkie.crm.shared;

import org.slf4j.MDC;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Background work (scheduled jobs, event listeners) runs with MDC {@code jobId} and {@code job}, so its log lines can
 * be followed like a request's. An existing {@code requestId} is kept; the previous MDC is restored exactly afterwards.
 */
public final class JobMdc {

    private JobMdc() {
    }

    public static <T> T call(String job, Supplier<T> work) {
        return call(job, false, work);
    }

    /** {@code keepJobId}: reuse a jobId already in MDC (a scheduled run) and only name the job. */
    public static <T> T call(String job, boolean keepJobId, Supplier<T> work) {
        Map<String, String> before = MDC.getCopyOfContextMap();
        try {
            if (!keepJobId || MDC.get("jobId") == null) {
                MDC.put("jobId", "job-" + UUID.randomUUID().toString().substring(0, 8));
            }
            MDC.put("job", job);
            return work.get();
        } finally {
            if (before == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(before);
            }
        }
    }

    /** For the task scheduler: names the job after the scheduled method. */
    public static Runnable decorate(Runnable task) {
        String job = task instanceof ScheduledMethodRunnable s
                ? s.getMethod().getDeclaringClass().getSimpleName() + "." + s.getMethod().getName()
                : task.getClass().getSimpleName();
        return () -> call(job, () -> {
            task.run();
            return null;
        });
    }
}
