package com.leadrat.aisdk.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai-sdk")
public class AuthController {

    private final PasswordStore passwordStore;
    private final JwtService jwtService;

    public AuthController(PasswordStore passwordStore, JwtService jwtService) {
        this.passwordStore = passwordStore;
        this.jwtService = jwtService;
    }

    public record TokenRequest(String password) {}

    @PostMapping("/auth/token")
    public ResponseEntity<?> token(@RequestBody TokenRequest request) {
        if (!passwordStore.isSetupCompleted()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(Map.of("error", "Setup has not been completed yet"));
        }
        if (request == null || request.password() == null || !passwordStore.matches(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid password"));
        }
        return ResponseEntity.ok(Map.of(
                "token", jwtService.issue("ai-sdk-admin"),
                "expiresIn", jwtService.expirySeconds()));
    }
}
