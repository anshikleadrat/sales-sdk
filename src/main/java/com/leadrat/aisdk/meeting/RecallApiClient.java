package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public class RecallApiClient {

    private static final Logger log = LoggerFactory.getLogger(RecallApiClient.class);

    private final RecallHttp http;
    private final AiSdkProperties properties;

    public RecallApiClient(RecallHttp http, AiSdkProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public String createBot(String meetingUrl, Map<String, Object> botConfig) {
        Map<String, Object> body = new LinkedHashMap<>(botConfig);
        body.put("meeting_url", meetingUrl);
        String raw = http.client().post().uri("/api/v1/bot/").body(body).retrieve().body(String.class);
        return http.requireId(http.parse(raw, "Recall create bot"), "Recall create bot");
    }

    public JsonNode retrieveBot(String botId) {
        String raw = http.client().get().uri("/api/v1/bot/{id}/", botId).retrieve().body(String.class);
        return http.parse(raw, "Recall retrieve bot");
    }

    public void deleteBot(String botId) {
        try {
            http.client().delete().uri("/api/v1/bot/{id}/", botId).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("ai-sdk: recall delete bot {} failed ({})", botId, e.getMessage());
        }
    }

    public String createTranscript(String recordingId) {
        Map<String, Object> body = Map.of(
                "provider", Map.of("recallai_async",
                        Map.of("language_code", properties.getMeeting().getRecall().getLanguage())),
                "diarization", Map.of("use_separate_streams_when_available", true));
        String raw = http.client().post()
                .uri("/api/v1/recording/{id}/create_transcript/", recordingId)
                .body(body).retrieve().body(String.class);
        return http.requireId(http.parse(raw, "Recall create transcript"), "Recall create transcript");
    }

    public JsonNode retrieveTranscript(String transcriptId) {
        String raw = http.client().get().uri("/api/v1/transcript/{id}/", transcriptId)
                .retrieve().body(String.class);
        return http.parse(raw, "Recall retrieve transcript");
    }

    public JsonNode downloadJson(String url) {
        String raw = http.plain().get().uri(URI.create(url)).retrieve().body(String.class);
        return http.parse(raw, "Recall transcript download");
    }
}
