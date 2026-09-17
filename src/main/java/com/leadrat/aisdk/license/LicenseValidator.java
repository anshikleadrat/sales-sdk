package com.leadrat.aisdk.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LicenseValidator implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(LicenseValidator.class);

    private final AiSdkProperties properties;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "ai-sdk-license");
                thread.setDaemon(true);
                return thread;
            });

    public LicenseValidator(AiSdkProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void start() {
        AiSdkProperties.License license = properties.getLicense();
        if (!StringUtils.hasText(license.getServerUrl()) || !StringUtils.hasText(license.getKey())) {
            log.info("ai-sdk: license check disabled (no license key or server url configured)");
            return;
        }
        long intervalHours = Math.max(1, license.getCheckIntervalHours());
        scheduler.scheduleAtFixedRate(this::check, 0, intervalHours, TimeUnit.HOURS);
    }

    public void check() {
        AiSdkProperties.License license = properties.getLicense();
        Map<String, Object> payload = Map.of(
                "licenseId", license.getKey(),
                "appHash", appHash(),
                "timestamp", Instant.now().toString());
        try {
            RestClient.create().post()
                    .uri(license.getServerUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("ai-sdk: license check failed ({})", e.toString());
        }
    }

    private String appHash() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(host.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }
}
