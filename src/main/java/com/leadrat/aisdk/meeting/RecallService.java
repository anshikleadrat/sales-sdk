package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecallService {

    private static final Logger log = LoggerFactory.getLogger(RecallService.class);
    private static final String PROVIDER = "RECALL_AI";

    private final MeetingStore meetingStore;
    private final RecallApiClient apiClient;
    private final RecallCalendarService calendarService;
    private final RecallPayloadMapper mapper;
    private final AiSdkProperties properties;

    public RecallService(MeetingStore meetingStore, RecallApiClient apiClient,
                         RecallCalendarService calendarService, RecallPayloadMapper mapper,
                         AiSdkProperties properties) {
        this.meetingStore = meetingStore;
        this.apiClient = apiClient;
        this.calendarService = calendarService;
        this.mapper = mapper;
        this.properties = properties;
    }

    public void scheduleBot(String meetingId) {
        meetingStore.find(meetingId).ifPresent(calendarService::scheduleBot);
    }

    public void removeBot(String meetingId) {
        meetingStore.find(meetingId).ifPresent(calendarService::unscheduleBot);
    }

    public void handleEvent(Map<String, Object> event) {
        String name = event.get("event") instanceof String s ? s : null;
        if (name == null) {
            return;
        }
        Map<String, Object> data = asMap(event.get("data"));
        switch (name) {
            case "calendar.sync_events" -> calendarService.syncCalendar(
                    str(data, "calendar_id"), instant(str(data, "last_updated_ts")));
            case "calendar.update" -> calendarService.refreshStatus(str(data, "calendar_id"));
            case "recording.done" -> onRecordingDone(data);
            case "transcript.done" -> onTranscriptDone(data);
            default -> {
                if (name.startsWith("bot.")) {
                    onBotStatusChange(name, data);
                }
            }
        }
    }

    private void onRecordingDone(Map<String, Object> data) {
        if (properties.getMeeting().getRecall().isAutoTranscribe()) {
            return;
        }
        String recordingId = str(asMap(data.get("recording")), "id");
        if (recordingId == null) {
            return;
        }
        try {
            apiClient.createTranscript(recordingId);
        } catch (Exception e) {
            log.warn("ai-sdk: recall create transcript failed for recording {} ({})", recordingId, e.getMessage());
        }
    }

    private void onBotStatusChange(String event, Map<String, Object> data) {
        String botId = str(asMap(data.get("bot")), "id");
        String status = "bot.status_change".equals(event)
                ? str(asMap(data.get("data")), "code")
                : event.substring("bot.".length());
        if (botId == null || status == null) {
            return;
        }
        Meeting meeting = findMeeting(botId, asMap(asMap(data.get("bot")).get("metadata")));
        if (meeting == null) {
            return;
        }
        meeting.setRecallBotStatus(status.toUpperCase());
        if (meeting.getRecallBotId() == null) {
            meeting.setRecallBotId(botId);
        }
        meetingStore.save(meeting);
    }

    private void onTranscriptDone(Map<String, Object> data) {
        String botId = str(asMap(data.get("bot")), "id");
        String transcriptId = str(asMap(data.get("transcript")), "id");
        if (botId == null || transcriptId == null) {
            return;
        }
        Map<String, Object> metadata = asMap(asMap(data.get("bot")).get("metadata"));
        JsonNode segments = fetchSegments(transcriptId);
        storeDiscussion(botId, segments, findMeeting(botId, metadata), metadata);
    }

    private JsonNode fetchSegments(String transcriptId) {
        try {
            JsonNode transcript = apiClient.retrieveTranscript(transcriptId);
            String downloadUrl = transcript.path("data").path("download_url").asString(null);
            return downloadUrl == null ? null : apiClient.downloadJson(downloadUrl);
        } catch (Exception e) {
            log.warn("ai-sdk: recall transcript {} fetch failed ({})", transcriptId, e.getMessage());
            return null;
        }
    }

    private void storeDiscussion(String botId, JsonNode segments, Meeting meeting,
                                 Map<String, Object> metadata) {
        RecallPayloadMapper.TranscriptText text = mapper.toDiscussion(segments);
        if (!properties.getMeeting().getRecall().isStoreTranscript()) {
            text = new RecallPayloadMapper.TranscriptText(null, text.participants());
        }
        Optional<Discussion> existing = meetingStore.findDiscussion(PROVIDER, botId);
        Instant now = Instant.now();
        Instant startedAt = meeting != null ? meeting.getScheduledAt()
                : existing.map(Discussion::startedAt).orElse(null);
        Instant occurredAt = startedAt != null ? startedAt : now;
        String leadId = meeting != null ? meeting.getLeadId() : str(metadata, "lead_id");
        String leadEntity = meeting != null ? meeting.getLeadEntity() : str(metadata, "lead_entity");
        List<Map<String, String>> participants = text.participants().isEmpty()
                ? existing.map(Discussion::participants).orElse(List.of())
                : text.participants();
        Discussion discussion = new Discussion(
                existing.map(Discussion::id).orElse(0L),
                meeting != null ? meeting.getId() : existing.map(Discussion::meetingId).orElse(null),
                leadEntity,
                leadId,
                PROVIDER,
                botId,
                meeting != null ? meeting.getTitle() : existing.map(Discussion::meetingTitle).orElse(null),
                meeting != null ? meeting.getMeetingLink() : existing.map(Discussion::meetingUrl).orElse(null),
                text.discussion(),
                participants,
                startedAt,
                now,
                occurredAt,
                now,
                leadId == null ? "UNMATCHED" : "MATCHED");
        meetingStore.saveDiscussion(discussion);
        if (meeting != null && meeting.isOpen()) {
            meeting.setStatus(MeetingStatus.COMPLETED);
            meetingStore.save(meeting);
        }
        if (leadId == null) {
            log.warn("ai-sdk: recall transcript for bot {} has no lead to attach to", botId);
        }
    }

    private Meeting findMeeting(String botId, Map<String, Object> metadata) {
        Meeting byBot = meetingStore.findByBotId(botId).orElse(null);
        if (byBot != null) {
            return byBot;
        }
        String meetingId = str(metadata, "meeting_id");
        return meetingId == null ? null : meetingStore.find(meetingId).orElse(null);
    }

    private String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private Instant instant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return Map.of();
    }
}
