package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/ai-sdk/meetings/google")
public class GoogleOAuthController {

    private static final String SCOPES = "https://www.googleapis.com/auth/calendar.events"
            + " https://www.googleapis.com/auth/userinfo.email";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    public static final String CALLBACK_PATH = "/ai-sdk/meetings/google/callback";

    private final AiSdkProperties properties;
    private final GoogleTokenStore tokenStore;
    private final GoogleCredentialStore credentialStore;
    private final RecallCalendarService recallCalendarService;
    private final ConcurrentHashMap<String, Instant> states = new ConcurrentHashMap<>();

    public GoogleOAuthController(AiSdkProperties properties, GoogleTokenStore tokenStore,
                                 GoogleCredentialStore credentialStore,
                                 RecallCalendarService recallCalendarService) {
        this.properties = properties;
        this.tokenStore = tokenStore;
        this.credentialStore = credentialStore;
        this.recallCalendarService = recallCalendarService;
    }

    @GetMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(HttpServletRequest request) {
        AiSdkProperties.Google google = properties.getMeeting().getGoogle();
        String nonce = UUID.randomUUID().toString();
        states.entrySet().removeIf(entry -> entry.getValue().isBefore(Instant.now()));
        states.put(nonce, Instant.now().plus(STATE_TTL));
        String url = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + enc(google.getClientId())
                + "&redirect_uri=" + enc(redirectUri(request))
                + "&response_type=code"
                + "&scope=" + enc(SCOPES)
                + "&access_type=offline&prompt=consent"
                + "&state=" + enc(nonce);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam String state,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        Instant expiry = states.remove(state);
        if (expiry == null || expiry.isBefore(Instant.now())) {
            response.sendRedirect(landing(request, "expired"));
            return;
        }
        AiSdkProperties.Google google = properties.getMeeting().getGoogle();
        @SuppressWarnings("unchecked")
        Map<String, Object> token = RestClient.create()
                .post()
                .uri("https://oauth2.googleapis.com/token")
                .body(Map.of(
                        "code", code,
                        "client_id", google.getClientId(),
                        "client_secret", google.getClientSecret(),
                        "redirect_uri", redirectUri(request),
                        "grant_type", "authorization_code"))
                .retrieve()
                .body(Map.class);
        if (token == null || token.get("refresh_token") == null || token.get("access_token") == null) {
            response.sendRedirect(landing(request, "failed"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = RestClient.create()
                .get()
                .uri("https://www.googleapis.com/oauth2/v2/userinfo")
                .header("Authorization", "Bearer " + token.get("access_token"))
                .retrieve()
                .body(Map.class);
        String email = profile == null || profile.get("email") == null ? "" : String.valueOf(profile.get("email"));
        tokenStore.saveRefreshToken(email, String.valueOf(token.get("refresh_token")), SCOPES);
        long expiresIn = token.get("expires_in") instanceof Number n ? n.longValue() : 3600L;
        tokenStore.cacheAccessToken(String.valueOf(token.get("access_token")), expiresIn);
        recallCalendarService.connect();
        response.sendRedirect(landing(request, "connected"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> disconnect() {
        recallCalendarService.disconnect();
        credentialStore.find().ifPresent(credential -> {
            try {
                RestClient.create()
                        .post()
                        .uri("https://oauth2.googleapis.com/revoke?token="
                                + enc(tokenStore.decryptRefreshToken(credential)))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ignored) {
                credential.setRecallError("revoke call failed");
            }
            credential.setRevokedAt(Instant.now());
            credentialStore.save(credential);
        });
        tokenStore.clearCache();
        return ResponseEntity.ok(Map.of("status", "disconnected"));
    }

    private String landing(HttpServletRequest request, String status) {
        String base = properties.getMeeting().getGoogle().getPostConnectRedirect();
        if (base == null || base.isBlank()) {
            base = redirectUri(request).replace("/ai-sdk/meetings/google/callback", "/ai-sdk/meetings");
        }
        return base + (base.contains("?") ? "&" : "?") + "google=" + status;
    }

    private String redirectUri(HttpServletRequest request) {
        String configured = properties.getMeeting().getGoogle().getRedirectUri();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return AiSdkUrls.externalBase(request) + CALLBACK_PATH;
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
