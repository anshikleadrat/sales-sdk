package com.leadrat.aisdk.whatsapp;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

public class EngagetoHttp {

    private final AiSdkProperties properties;
    private final ObjectMapper objectMapper;

    public EngagetoHttp(AiSdkProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        AiSdkProperties.Whatsapp whatsapp = properties.getWhatsapp();
        return whatsapp.isActive() && whatsapp.getApiKey() != null && !whatsapp.getApiKey().isBlank();
    }

    public RestClient client() {
        return RestClient.builder()
                .baseUrl(properties.getWhatsapp().getBaseUrl())
                .defaultHeader("Api-Key", properties.getWhatsapp().getApiKey())
                .defaultHeader("accept", "*/*")
                .requestFactory(factory(Duration.ofSeconds(10), Duration.ofSeconds(30)))
                .build();
    }

    public JsonNode parse(String raw, String context) {
        try {
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (RuntimeException e) {
            throw new IllegalStateException(context + " parse failed", e);
        }
    }

    private SimpleClientHttpRequestFactory factory(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }
}
