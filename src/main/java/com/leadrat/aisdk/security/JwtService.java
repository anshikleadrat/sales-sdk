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

    public JwtService(AiSdkProperties properties, SdkCredentials credentials) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(credentials.jwtSecret().getBytes(StandardCharsets.UTF_8));
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
