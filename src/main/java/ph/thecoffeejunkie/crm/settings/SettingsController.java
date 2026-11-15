package ph.thecoffeejunkie.crm.settings;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
class SettingsController {

    private final Settings settings;

    SettingsController(Settings settings) {
        this.settings = settings;
    }

    @GetMapping
    Map<String, Object> get() {
        return settings.all();
    }

    @PutMapping
    Map<String, Object> put(@RequestBody Map<String, JsonNode> body) {
        Map<SettingKey, JsonNode> values = new LinkedHashMap<>();
        body.forEach((k, v) -> values.put(SettingKey.of(k).orElseThrow(() -> new DomainException(
                ErrorCode.VALIDATION_FAILED, "Unknown setting " + k, Map.of("field", k))), v));
        settings.update(values);
        return settings.all();
    }
}
