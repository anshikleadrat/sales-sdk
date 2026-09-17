package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class RecallBotConfigFactory {

    private final AiSdkProperties properties;

    public RecallBotConfigFactory(AiSdkProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> botConfig(Instant startAt, Map<String, String> metadata) {
        AiSdkProperties.Recall recall = properties.getMeeting().getRecall();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("bot_name", recall.getBotName());
        config.put("metadata", metadata);
        Instant joinAt = joinAt(startAt);
        if (joinAt != null) {
            config.put("join_at", joinAt.toString());
        }
        if (recall.isAutoTranscribe()) {
            config.put("recording_config", Map.of(
                    "transcript", Map.of("provider", Map.of("recallai_streaming",
                            Map.of("language_code", recall.getLanguage(),
                                    "mode", recall.getTranscriptMode()))),
                    "participant_events", Map.of(),
                    "meeting_metadata", Map.of()));
        }
        return config;
    }

    public String deduplicationKey(Instant startAt, String meetingUrl) {
        return (startAt == null ? "unknown" : startAt.toString()) + "-"
                + (meetingUrl == null ? "unknown" : meetingUrl);
    }

    private Instant joinAt(Instant startAt) {
        if (startAt == null) {
            return null;
        }
        Instant joinAt = startAt.minus(
                Duration.ofMinutes(properties.getMeeting().getRecall().getJoinEarlyMinutes()));
        return joinAt.isBefore(Instant.now()) ? null : joinAt;
    }
}
