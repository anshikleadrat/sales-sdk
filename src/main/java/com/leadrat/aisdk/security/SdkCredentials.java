package com.leadrat.aisdk.security;

import com.leadrat.aisdk.config.AiSdkProperties;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class SdkCredentials {

    private static final String JWT_SECRET = "jwt-secret";
    private static final String SETUP_OTP = "setup-otp";
    private static final String OTP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int OTP_LENGTH = 10;
    private static final int SECRET_BYTES = 48;
    private static final int MIN_SECRET_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final String jwtSecret;
    private final String setupOtp;
    private final boolean jwtSecretGenerated;
    private final boolean setupOtpGenerated;

    public SdkCredentials(AiSdkProperties properties, SecretStore store) {
        String configuredSecret = trimToNull(properties.getSecurity().getJwtSecret());
        if (configuredSecret != null && configuredSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("ai-sdk.security.jwt-secret must be at least 256 bits (32 bytes)");
        }
        this.jwtSecretGenerated = configuredSecret == null;
        this.jwtSecret = configuredSecret != null ? configuredSecret : store.getOrCreate(JWT_SECRET, this::randomSecret);

        String configuredOtp = trimToNull(properties.getSecurity().getOtp());
        this.setupOtpGenerated = configuredOtp == null;
        this.setupOtp = configuredOtp != null ? configuredOtp : store.getOrCreate(SETUP_OTP, this::randomOtp);
    }

    public String jwtSecret() {
        return jwtSecret;
    }

    public String setupOtp() {
        return setupOtp;
    }

    public boolean jwtSecretGenerated() {
        return jwtSecretGenerated;
    }

    public boolean setupOtpGenerated() {
        return setupOtpGenerated;
    }

    private String randomSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String randomOtp() {
        StringBuilder otp = new StringBuilder(OTP_LENGTH);
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(OTP_ALPHABET.charAt(random.nextInt(OTP_ALPHABET.length())));
        }
        return otp.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
