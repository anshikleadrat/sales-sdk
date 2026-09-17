package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class GoogleTokenStore {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenStore.class);

    private final AiSdkProperties properties;
    private final GoogleCredentialStore credentialStore;
    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    public GoogleTokenStore(AiSdkProperties properties, GoogleCredentialStore credentialStore) {
        this.properties = properties;
        this.credentialStore = credentialStore;
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean valid() {
            return accessToken != null && expiresAt != null && expiresAt.isAfter(Instant.now());
        }
    }

    public void saveRefreshToken(String googleEmail, String refreshToken, String scopes) {
        GoogleCredential credential = credentialStore.find().orElseGet(GoogleCredential::new);
        credential.setGoogleEmail(googleEmail);
        credential.setRefreshTokenEncrypted(encrypt(refreshToken));
        credential.setScopes(scopes);
        credential.setConnectedAt(Instant.now());
        credential.setRevokedAt(null);
        credentialStore.save(credential);
    }

    public String decryptRefreshToken(GoogleCredential credential) {
        return decrypt(credential.getRefreshTokenEncrypted());
    }

    public void cacheAccessToken(String accessToken, long expiresInSeconds) {
        cache.set(new CachedToken(accessToken,
                Instant.now().plusSeconds(Math.max(60, expiresInSeconds - 60))));
    }

    public void clearCache() {
        cache.set(null);
    }

    public boolean connected() {
        return credentialStore.findConnected().isPresent();
    }

    public String accessToken() {
        CachedToken cached = cache.get();
        if (cached != null && cached.valid()) {
            return cached.accessToken();
        }
        GoogleCredential credential = credentialStore.findConnected()
                .orElseThrow(() -> new IllegalStateException("Google is not connected"));
        return refreshAccessToken(credential);
    }

    public String refreshAccessToken(GoogleCredential credential) {
        String refreshToken = decryptRefreshToken(credential);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = RestClient.create()
                    .post()
                    .uri("https://oauth2.googleapis.com/token")
                    .body(Map.of(
                            "client_id", properties.getMeeting().getGoogle().getClientId(),
                            "client_secret", properties.getMeeting().getGoogle().getClientSecret(),
                            "refresh_token", refreshToken,
                            "grant_type", "refresh_token"))
                    .retrieve()
                    .body(Map.class);
            if (response == null || response.get("access_token") == null) {
                throw new IllegalStateException("Google token refresh failed");
            }
            String accessToken = String.valueOf(response.get("access_token"));
            long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 3600L;
            cacheAccessToken(accessToken, expiresIn);
            credential.setLastRefreshAt(Instant.now());
            credentialStore.save(credential);
            return accessToken;
        } catch (Exception e) {
            log.warn("ai-sdk: google token refresh failed ({})", e.getMessage());
            throw new IllegalStateException("Google token refresh failed", e);
        }
    }

    private SecretKeySpec key() {
        String configured = properties.getMeeting().getGoogle().getTokenEncryptionKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("ai-sdk.meeting.google.token-encryption-key is not configured");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(configured);
        } catch (RuntimeException e) {
            raw = configured.getBytes(StandardCharsets.UTF_8);
        }
        byte[] keyBytes = new byte[32];
        System.arraycopy(raw, 0, keyBytes, 0, Math.min(raw.length, 32));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Token encryption failed", e);
        }
    }

    private String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[12];
            byte[] cipherText = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, 12);
            System.arraycopy(combined, 12, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Token decryption failed", e);
        }
    }
}
