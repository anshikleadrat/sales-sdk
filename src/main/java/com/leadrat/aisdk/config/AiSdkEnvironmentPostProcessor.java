package com.leadrat.aisdk.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class AiSdkEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "aiSdkHostDefaults";

    private static final Map<String, String> FALLBACKS = Map.of(
            "ai-sdk.llm.api-key", "OPENROUTER_API_KEY",
            "ai-sdk.llm.base-url", "OPENROUTER_BASE_URL");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        FALLBACKS.forEach((property, hostVariable) -> {
            if (!hasText(environment.getProperty(property))) {
                String value = environment.getProperty(hostVariable);
                if (hasText(value)) {
                    defaults.put(property, value);
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
