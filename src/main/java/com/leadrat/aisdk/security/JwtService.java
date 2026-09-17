package com.leadrat.aisdk.security;

import com.leadrat.aisdk.config.AiSdkProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public class JwtService {

    private final AiSdkProperties properties;
    private final SecretKey key;

    public JwtService(AiSdkProperties properties) {
        this.properties = properties;
        String secret = properties.getSecurity().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("ai-sdk.security.jwt-secret must be set and at least 256 bits (32 bytes)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String subject) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getSecurity().getJwtExpiryMinutes() * 60L);
        return Jwts.builder()
                .subject(subject)
                .issuer("ai-sdk")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long expirySeconds() {
        return properties.getSecurity().getJwtExpiryMinutes() * 60L;
    }

    public Claims verify(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer("ai-sdk").build()
                .parseSignedClaims(token).getPayload();
    }
}
