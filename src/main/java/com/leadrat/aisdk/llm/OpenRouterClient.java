package com.leadrat.aisdk.llm;

import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.llm.dto.ChatCompletionRequest;
import com.leadrat.aisdk.llm.dto.ChatCompletionResponse;
import com.leadrat.aisdk.llm.dto.ChatMessage;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class OpenRouterClient {

    private final RestClient restClient;
    private final AiSdkProperties properties;

    public OpenRouterClient(AiSdkProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getLlm().getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getLlm().getTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getLlm().getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Title", "ai-query-sdk")
                .build();
    }

    public String complete(String model, List<ChatMessage> messages, Map<String, Object> responseFormat) {
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter API key is not configured. Set the OPENROUTER_API_KEY environment variable or ai-sdk.llm.api-key.");
        }
        ChatCompletionRequest request = new ChatCompletionRequest(model, messages, 0.0, responseFormat);
        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
        return response == null ? "" : response.firstContent();
    }
}
