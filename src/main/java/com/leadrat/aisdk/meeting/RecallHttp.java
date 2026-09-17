package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

public class RecallHttp {

    private final AiSdkProperties properties;
    private final ObjectMapper objectMapper;

    public RecallHttp(AiSdkProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        AiSdkProperties.Recall recall = properties.getMeeting().getRecall();
        return recall.isEnabled() && recall.getApiKey() != null && !recall.getApiKey().isBlank();
    }

    public RestClient client() {
        return RestClient.builder()
                .baseUrl(properties.getMeeting().getRecall().getBaseUrl())
                .defaultHeader("Authorization", authHeader())
                .defaultHeader("accept", "application/json")
                .requestFactory(factory(Duration.ofSeconds(15), Duration.ofSeconds(60)))
                .build();
    }

    public RestClient plain() {
        return RestClient.builder()
                .requestFactory(factory(Duration.ofSeconds(15), Duration.ofSeconds(60)))
                .build();
    }

    public JsonNode parse(String raw, String context) {
        try {
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (RuntimeException e) {
            throw new IllegalStateException(context + " parse failed", e);
        }
    }

    public String requireId(JsonNode node, String context) {
        String id = node.path("id").asString(null);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(context + " returned no id");
        }
        return id;
    }

    private String authHeader() {
        String key = properties.getMeeting().getRecall().getApiKey();
        key = key == null ? "" : key.trim();
        return key.startsWith("Token ") ? key : "Token " + key;
    }

    private SimpleClientHttpRequestFactory factory(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return factory;
    }
}
