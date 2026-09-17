package com.leadrat.aisdk.llm;

import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.llm.dto.ChatCompletionRequest;
import com.leadrat.aisdk.llm.dto.ChatCompletionResponse;
import com.leadrat.aisdk.llm.dto.ChatMessage;
import com.leadrat.aisdk.llm.dto.ModelListResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class OpenRouterClient {

    private record Bound(String baseUrl, RestClient client) {
    }

    private final AiSdkProperties properties;
    private final AtomicReference<Bound> bound = new AtomicReference<>();

    public OpenRouterClient(AiSdkProperties properties) {
        this.properties = properties;
    }

    public boolean configured() {
        String apiKey = properties.getLlm().getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    public String complete(String model, List<ChatMessage> messages, Map<String, Object> responseFormat) {
        String apiKey = properties.getLlm().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No OpenRouter API key is configured. Add one on the SDK's keys page "
                    + "(/ai-sdk/settings), or set OPENROUTER_API_KEY in the host application.");
        }
        ChatCompletionRequest request = new ChatCompletionRequest(model, messages, 0.0, responseFormat);
        ChatCompletionResponse response = client().post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
        return response == null ? "" : response.firstContent();
    }

    public List<String> models() {
        try {
            ModelListResponse response = client().get()
                    .uri("/models")
                    .retrieve()
                    .body(ModelListResponse.class);
            return response == null ? List.of() : response.ids();
        } catch (Exception e) {
            return List.of();
        }
    }

    private RestClient client() {
        String baseUrl = properties.getLlm().getBaseUrl();
        Bound current = bound.get();
        if (current != null && current.baseUrl().equals(baseUrl)) {
            return current.client();
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getLlm().getTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getLlm().getTimeoutSeconds()));
        RestClient built = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Title", "ai-query-sdk")
                .build();
        bound.set(new Bound(baseUrl, built));
        return built;
    }
}
