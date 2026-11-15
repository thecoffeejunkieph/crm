package ph.thecoffeejunkie.crm.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditChanges;
import ph.thecoffeejunkie.crm.audit.AuditLog;

/**
 * Switches the app's own log detail at runtime without a redeploy (spec 2026-11-05 logging, §6). Only the
 * {@code ph.thecoffeejunkie.crm} logger and only DEBUG/INFO/WARN; resets to the configured level on restart.
 */
@RestController
@RequestMapping("/api/admin/log-level")
@PreAuthorize("hasRole('ADMIN')")
class LogLevelController {

    static final String APP = "ph.thecoffeejunkie.crm";

    record LogLevelBody(@NotNull @Pattern(regexp = "DEBUG|INFO|WARN") String level) {
    }

    private final LoggingSystem logging;
    private final AuditLog audit;

    LogLevelController(LoggingSystem logging, AuditLog audit) {
        this.logging = logging;
        this.audit = audit;
    }

    @GetMapping
    LogLevelBody get() {
        return new LogLevelBody(logging.getLoggerConfiguration(APP).getEffectiveLevel().name());
    }

    @PutMapping
    @Transactional
    LogLevelBody set(@Valid @RequestBody LogLevelBody body) {
        String before = get().level();
        logging.setLogLevel(APP, LogLevel.valueOf(body.level()));
        audit.record(AuditAction.SETTING_CHANGED, "SETTING", null, "Log level " + before + " → " + body.level(),
                AuditChanges.diff().field("logLevel", before, body.level()));
        return get();
    }
}
