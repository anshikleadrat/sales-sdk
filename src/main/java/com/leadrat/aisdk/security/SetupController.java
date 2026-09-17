package com.leadrat.aisdk.security;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/ai-sdk")
public class SetupController {

    private final AiSdkProperties properties;
    private final PasswordStore passwordStore;

    public SetupController(AiSdkProperties properties, PasswordStore passwordStore) {
        this.properties = properties;
        this.passwordStore = passwordStore;
    }

    public record SetupRequest(String otp, String password) {}

    @PostMapping("/setup")
    public ResponseEntity<?> setup(@RequestBody SetupRequest request) {
        String configuredOtp = properties.getSecurity().getOtp();
        if (configuredOtp == null || configuredOtp.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "No setup OTP configured for this deployment"));
        }
        if (request == null || request.otp() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "otp and password are required"));
        }
        if (!constantTimeEquals(configuredOtp, request.otp())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid OTP"));
        }
        if (request.password().length() < 12) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 12 characters"));
        }
        if (passwordStore.isSetupCompleted()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Setup has already been completed"));
        }
        passwordStore.store(request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "setup-complete"));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
