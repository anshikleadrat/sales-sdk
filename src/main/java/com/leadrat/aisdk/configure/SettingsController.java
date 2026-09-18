package com.leadrat.aisdk.configure;

import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.config.AiSdkSettings;
import com.leadrat.aisdk.meeting.AiSdkUrls;
import com.leadrat.aisdk.meeting.GoogleOAuthController;
import com.leadrat.aisdk.llm.OpenRouterClient;
import com.leadrat.aisdk.meeting.MeetingReconciler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai-sdk/configure/settings")
public class SettingsController {

    private final AiSdkSettings settings;
    private final MeetingReconciler reconciler;
    private final OpenRouterClient llmClient;

    public SettingsController(AiSdkSettings settings, MeetingReconciler reconciler, OpenRouterClient llmClient) {
        this.settings = settings;
        this.reconciler = reconciler;
        this.llmClient = llmClient;
    }

    @GetMapping
    public Map<String, Object> get(HttpServletRequest request) {
        return body(request);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, String> values,
                                                    HttpServletRequest request) {
        try {
            settings.save(values);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        reconciler.start();
        return ResponseEntity.ok(body(request));
    }

    @GetMapping("/models")
    public Map<String, Object> models() {
        List<String> ids = llmClient.models();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("models", ids);
        out.put("source", ids.isEmpty() ? "unavailable" : settings.properties().getLlm().getBaseUrl());
        return out;
    }

    private Map<String, Object> body(HttpServletRequest request) {
        AiSdkProperties properties = settings.properties();
        AiSdkProperties.Google google = properties.getMeeting().getGoogle();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settings", settings.snapshot());
        out.put("missing", settings.missing());
        out.put("queryReady", settings.missing().isEmpty());
        out.put("meetingEnabled", properties.getMeeting().isActive());
        out.put("googleEnabled", google.isActive());
        out.put("recallEnabled", properties.getMeeting().getRecall().isActive());
        out.put("whatsappEnabled", properties.getWhatsapp().isActive());
        out.put("effectiveRedirectUri", google.getRedirectUri() == null || google.getRedirectUri().isBlank()
                ? AiSdkUrls.externalBase(request) + GoogleOAuthController.CALLBACK_PATH
                : google.getRedirectUri());
        out.put("webhookUrl", AiSdkUrls.externalBase(request) + "/ai-sdk/webhooks/recall-ai");
        out.put("storagePath", properties.getStorage().getSqlitePath());
        return out;
    }
}
