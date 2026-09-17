package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/ai-sdk/webhooks/recall-ai")
public class RecallWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RecallWebhookController.class);

    private final AiSdkProperties properties;
    private final RecallService recallService;
    private final ObjectMapper objectMapper;

    public RecallWebhookController(AiSdkProperties properties, RecallService recallService,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.recallService = recallService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody String rawBody, HttpServletRequest request) {
        if (!properties.getMeeting().getRecall().isActive()) {
            return ResponseEntity.status(503).body(Map.of("error", "disabled"));
        }
        if (!verify(rawBody, header(request, "id"), header(request, "timestamp"), header(request, "signature"))) {
            return ResponseEntity.status(401).body(Map.of("error", "bad signature"));
        }
        Map<String, Object> event;
        try {
            event = objectMapper.readValue(rawBody, Map.class);
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", "bad payload"));
        }
        try {
            recallService.handleEvent(event);
            return ResponseEntity.ok(Map.of("status", "accepted"));
        } catch (Exception e) {
            log.warn("ai-sdk: recall ingest failed ({})", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "ingest failed"));
        }
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader("webhook-" + name);
        return value != null ? value : request.getHeader("svix-" + name);
    }

    private boolean verify(String rawBody, String id, String timestamp, String signatures) {
        String secret = properties.getMeeting().getRecall().getWebhookSecret();
        if (secret == null || secret.isBlank() || id == null || timestamp == null
                || signatures == null || rawBody == null) {
            return false;
        }
        try {
            long eventTime = Long.parseLong(timestamp);
            if (Duration.between(Instant.ofEpochSecond(eventTime), Instant.now()).abs().getSeconds() > 300) {
                return false;
            }
            byte[] key = Base64.getDecoder().decode(secret.replace("whsec_", ""));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((id + "." + timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(digest);
            for (String candidate : signatures.split(" ")) {
                String[] parts = candidate.split(",", 2);
                if (parts.length == 2 && !"v1".equals(parts[0])) {
                    continue;
                }
                if (constantTime(expected, (parts.length == 2 ? parts[1] : candidate).trim())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean constantTime(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
