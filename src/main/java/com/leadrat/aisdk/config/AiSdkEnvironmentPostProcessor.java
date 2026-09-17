package com.leadrat.aisdk.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

public class AiSdkEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "aiSdkHostDefaults";

    private static final Map<String, List<String>> FALLBACKS = Map.ofEntries(
            entry("ai-sdk.llm.api-key", List.of("OPENROUTER_API_KEY")),
            entry("ai-sdk.llm.base-url", List.of("OPENROUTER_BASE_URL")),
            entry("ai-sdk.llm.planner-model", List.of("OPENROUTER_MODEL")),
            entry("ai-sdk.llm.summarizer-model", List.of("OPENROUTER_MODEL")),
            entry("ai-sdk.meeting.google.client-id", List.of("GOOGLE_CLIENT_ID", "GOOGLE_OAUTH_CLIENT_ID")),
            entry("ai-sdk.meeting.google.client-secret", List.of("GOOGLE_CLIENT_SECRET", "GOOGLE_OAUTH_CLIENT_SECRET")),
            entry("ai-sdk.meeting.google.redirect-uri", List.of("GOOGLE_REDIRECT_URI", "GOOGLE_OAUTH_REDIRECT_URI")),
            entry("ai-sdk.meeting.google.token-encryption-key",
                    List.of("GOOGLE_TOKEN_ENCRYPTION_KEY", "GOOGLE_TOKEN_ENC_KEY")),
            entry("ai-sdk.meeting.recall.api-key", List.of("RECALL_API_KEY", "RECALL_AI_API_KEY")),
            entry("ai-sdk.meeting.recall.webhook-secret", List.of("RECALL_WEBHOOK_SECRET", "RECALL_AI_WEBHOOK_SECRET")),
            entry("ai-sdk.meeting.recall.base-url", List.of("RECALL_BASE_URL", "RECALL_AI_BASE_URL")));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        FALLBACKS.forEach((property, hostVariables) -> {
            if (hasText(environment.getProperty(property))) {
                return;
            }
            for (String hostVariable : hostVariables) {
                String value = environment.getProperty(hostVariable);
                if (hasText(value)) {
                    defaults.put(property, value);
                    return;
                }
            }
        });
        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
