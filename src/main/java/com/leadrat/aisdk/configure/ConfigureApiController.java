package com.leadrat.aisdk.configure;

import com.leadrat.aisdk.audit.AuditLogService;
import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.config.ReadOnlyEntityManagerProvider;
import com.leadrat.aisdk.introspection.SchemaIntrospector;
import com.leadrat.aisdk.security.PasswordStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai-sdk")
public class ConfigureApiController {

    private final ConfigRepository configRepository;
    private final SchemaIntrospector introspector;
    private final PasswordStore passwordStore;
    private final ReadOnlyEntityManagerProvider readOnlyProvider;
    private final AuditLogService auditLog;
    private final AiSdkProperties properties;

    public ConfigureApiController(ConfigRepository configRepository, SchemaIntrospector introspector,
                                  PasswordStore passwordStore, ReadOnlyEntityManagerProvider readOnlyProvider,
                                  AuditLogService auditLog, AiSdkProperties properties) {
        this.configRepository = configRepository;
        this.introspector = introspector;
        this.passwordStore = passwordStore;
        this.readOnlyProvider = readOnlyProvider;
        this.auditLog = auditLog;
        this.properties = properties;
    }

    public record EntitySelectionRequest(List<EntityConfig> entities, List<FieldConfig> fields,
                                         List<RelationshipConfig> relationships) {}

    public record GuardrailRequest(List<GuardrailConfig> guardrails) {}

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("setupCompleted", passwordStore.isSetupCompleted());
        status.put("otpConfigured", properties.getSecurity().getOtp() != null && !properties.getSecurity().getOtp().isBlank());
        status.put("dedicatedReadOnlyDatasource", readOnlyProvider.isDedicated());
        return status;
    }

    @GetMapping("/configure/schema")
    public Map<String, Object> schema() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entities", configRepository.entities());
        out.put("fields", configRepository.fields());
        out.put("relationships", configRepository.relationships());
        out.put("guardrails", configRepository.guardrails());
        out.put("configVersion", configRepository.configVersion());
        out.put("limits", properties.getQuery());
        out.put("dedicatedReadOnlyDatasource", readOnlyProvider.isDedicated());
        return out;
    }

    @PostMapping("/configure/entities")
    public ResponseEntity<?> saveEntities(@RequestBody EntitySelectionRequest request) {
        configRepository.saveEntitySelection(
                request.entities() == null ? List.of() : request.entities(),
                request.fields() == null ? List.of() : request.fields(),
                request.relationships() == null ? List.of() : request.relationships());
        return ResponseEntity.ok(Map.of("status", "saved", "configVersion", configRepository.configVersion()));
    }

    @PostMapping("/configure/guardrails")
    public ResponseEntity<?> saveGuardrails(@RequestBody GuardrailRequest request) {
        configRepository.saveGuardrails(request.guardrails() == null ? List.of() : request.guardrails());
        return ResponseEntity.ok(Map.of("status", "saved", "configVersion", configRepository.configVersion()));
    }

    @PostMapping("/configure/rescan")
    public ResponseEntity<?> rescan() {
        int count = introspector.scan();
        return ResponseEntity.ok(Map.of("status", "rescanned", "entities", count,
                "configVersion", configRepository.configVersion()));
    }

    @GetMapping("/configure/audit")
    public List<Map<String, Object>> audit(@RequestParam(defaultValue = "50") int limit) {
        return auditLog.recent(Math.min(Math.max(limit, 1), 200));
    }
}
