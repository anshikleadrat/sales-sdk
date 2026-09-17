package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class RecallCalendarService {

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SCHEDULED = "SCHEDULED";
    static final String STATUS_FAILED = "FAILED";

    private static final Logger log = LoggerFactory.getLogger(RecallCalendarService.class);

    private final RecallHttp http;
    private final RecallCalendarClient calendarClient;
    private final RecallApiClient apiClient;
    private final RecallBotConfigFactory botConfigFactory;
    private final AiSdkProperties properties;
    private final GoogleCredentialStore credentialStore;
    private final GoogleTokenStore tokenStore;
    private final MeetingStore meetingStore;

    public RecallCalendarService(RecallHttp http, RecallCalendarClient calendarClient, RecallApiClient apiClient,
                                 RecallBotConfigFactory botConfigFactory, AiSdkProperties properties,
                                 GoogleCredentialStore credentialStore, GoogleTokenStore tokenStore,
                                 MeetingStore meetingStore) {
        this.http = http;
        this.calendarClient = calendarClient;
        this.apiClient = apiClient;
        this.botConfigFactory = botConfigFactory;
        this.properties = properties;
        this.credentialStore = credentialStore;
        this.tokenStore = tokenStore;
        this.meetingStore = meetingStore;
    }

    public void connect() {
        if (!http.configured()) {
            return;
        }
        GoogleCredential credential = credentialStore.findConnected().orElse(null);
        if (credential == null) {
            return;
        }
        String previous = credential.getRecallCalendarId();
        AiSdkProperties.Google google = properties.getMeeting().getGoogle();
        try {
            JsonNode calendar = calendarClient.createCalendar(
                    google.getClientId(),
                    google.getClientSecret(),
                    tokenStore.decryptRefreshToken(credential),
                    credential.getGoogleEmail(),
                    Map.of("source", "ai-query-sdk"));
            credential.setRecallCalendarId(calendar.path("id").asString(null));
            credential.setRecallStatus(calendar.path("status").asString("connecting"));
            credential.setRecallSyncedAt(Instant.now());
            credential.setRecallError(null);
            if (previous != null && !previous.equals(credential.getRecallCalendarId())) {
                calendarClient.deleteCalendar(previous);
            }
        } catch (Exception e) {
            credential.setRecallStatus("disconnected");
            credential.setRecallError(trim(e.getMessage()));
            log.warn("ai-sdk: recall calendar connect failed ({})", e.getMessage());
        }
        credentialStore.save(credential);
    }

    public void disconnect() {
        credentialStore.find().ifPresent(credential -> {
            if (credential.getRecallCalendarId() != null && http.configured()) {
                calendarClient.deleteCalendar(credential.getRecallCalendarId());
            }
            credential.setRecallCalendarId(null);
            credential.setRecallStatus(null);
            credential.setRecallError(null);
            credentialStore.save(credential);
        });
    }

    public void refreshStatus(String recallCalendarId) {
        if (!http.configured()) {
            return;
        }
        GoogleCredential credential = credentialStore.findByRecallCalendarId(recallCalendarId).orElse(null);
        if (credential == null) {
            return;
        }
        try {
            JsonNode calendar = calendarClient.retrieveCalendar(recallCalendarId);
            credential.setRecallStatus(calendar.path("status").asString(null));
            credential.setRecallError(null);
        } catch (Exception e) {
            credential.setRecallError(trim(e.getMessage()));
        }
        credential.setRecallSyncedAt(Instant.now());
        credentialStore.save(credential);
    }

    public void syncCalendar(String recallCalendarId, Instant updatedSince) {
        if (!http.configured() || credentialStore.findByRecallCalendarId(recallCalendarId).isEmpty()) {
            return;
        }
        List<JsonNode> events;
        try {
            events = calendarClient.findEventsUpdatedSince(recallCalendarId, updatedSince);
        } catch (Exception e) {
            log.warn("ai-sdk: recall list events failed for calendar {} ({})", recallCalendarId, e.getMessage());
            return;
        }
        events.forEach(this::applyEvent);
    }

    public void scheduleBot(Meeting meeting) {
        if (!http.configured() || meeting == null) {
            return;
        }
        if (meeting.getCalendarIcalUid() == null && meeting.getRecallCalendarEventId() == null) {
            sendDirectlyWithoutCalendar(meeting);
            return;
        }
        if (meeting.getRecallBotId() != null && !STATUS_FAILED.equals(meeting.getRecallBotStatus())) {
            return;
        }
        if (tooLate(meeting)) {
            return;
        }
        try {
            String eventId = meeting.getRecallCalendarEventId() != null
                    ? meeting.getRecallCalendarEventId()
                    : resolveEventId(meeting);
            if (eventId == null) {
                if (!sentDirectly(meeting)) {
                    meeting.setRecallBotStatus(STATUS_PENDING);
                    meeting.setRecallError("Calendar event not synced to Recall yet");
                }
            } else {
                meeting.setRecallCalendarEventId(eventId);
                JsonNode event = calendarClient.scheduleBot(eventId,
                        botConfigFactory.deduplicationKey(meeting.getScheduledAt(), meeting.getMeetingLink()),
                        botConfigFactory.botConfig(meeting.getScheduledAt(), metadata(meeting)));
                meeting.setRecallBotId(firstBotId(event));
                meeting.setRecallBotStatus(STATUS_SCHEDULED);
                meeting.setRecallScheduledAt(Instant.now());
                meeting.setRecallError(null);
            }
        } catch (Exception e) {
            meeting.setRecallBotStatus(STATUS_FAILED);
            meeting.setRecallError(trim(e.getMessage()));
            log.warn("ai-sdk: recall schedule bot failed for meeting {} ({})", meeting.getId(), e.getMessage());
        }
        meetingStore.save(meeting);
    }

    public void unscheduleBot(Meeting meeting) {
        if (meeting == null) {
            return;
        }
        if (http.configured()) {
            if (meeting.getRecallCalendarEventId() != null) {
                calendarClient.unscheduleBot(meeting.getRecallCalendarEventId());
            } else if (meeting.getRecallBotId() != null) {
                apiClient.deleteBot(meeting.getRecallBotId());
            }
        }
        meeting.setRecallBotId(null);
        meeting.setRecallBotStatus(null);
        meeting.setRecallScheduledAt(null);
        meetingStore.save(meeting);
    }

    private void sendDirectlyWithoutCalendar(Meeting meeting) {
        if (meeting.getRecallBotId() != null && !STATUS_FAILED.equals(meeting.getRecallBotStatus())) {
            return;
        }
        if (tooLate(meeting)) {
            return;
        }
        try {
            if (!sentDirectly(meeting)) {
                meeting.setRecallBotStatus(STATUS_PENDING);
                meeting.setRecallError("Waiting for the join window to send a bot");
            }
        } catch (Exception e) {
            meeting.setRecallBotStatus(STATUS_FAILED);
            meeting.setRecallError(trim(e.getMessage()));
            log.warn("ai-sdk: recall direct bot failed for meeting {} ({})", meeting.getId(), e.getMessage());
        }
        meetingStore.save(meeting);
    }

    private boolean tooLate(Meeting meeting) {
        return meeting.getScheduledAt() != null && meeting.getScheduledAt().isBefore(Instant.now()
                .minus(Duration.ofMinutes(properties.getMeeting().getRecall().getJoinGraceMinutes())));
    }

    private boolean sentDirectly(Meeting meeting) {
        String meetingUrl = meeting.getMeetingLink();
        if (meetingUrl == null || meetingUrl.isBlank() || meeting.getScheduledAt() == null) {
            return false;
        }
        if (meeting.getScheduledAt().isAfter(Instant.now().plus(
                Duration.ofMinutes(properties.getMeeting().getRecall().getDirectBotWindowMinutes())))) {
            return false;
        }
        meeting.setRecallBotId(apiClient.createBot(meetingUrl,
                botConfigFactory.botConfig(meeting.getScheduledAt(), metadata(meeting))));
        meeting.setRecallBotStatus(STATUS_SCHEDULED);
        meeting.setRecallScheduledAt(Instant.now());
        meeting.setRecallError(null);
        return true;
    }

    private void applyEvent(JsonNode event) {
        String icalUid = event.path("ical_uid").asString(null);
        if (icalUid == null || icalUid.isBlank()) {
            return;
        }
        String eventId = event.path("id").asString(null);
        boolean deleted = event.path("is_deleted").asBoolean(false);
        String meetingUrl = event.path("meeting_url").asString(null);
        for (Meeting meeting : meetingStore.findByIcalUid(icalUid)) {
            if (deleted) {
                unscheduleBot(meeting);
                continue;
            }
            meeting.setRecallCalendarEventId(eventId);
            if (meetingUrl != null && !meetingUrl.isBlank()) {
                meeting.setMeetingLink(meetingUrl);
            }
            String botId = firstBotId(event);
            if (botId == null && meeting.getRecallBotId() != null) {
                meetingStore.save(meeting);
                continue;
            }
            if (botId != null) {
                meeting.setRecallBotId(botId);
                if (notYetJoined(meeting.getRecallBotStatus())) {
                    meeting.setRecallBotStatus(STATUS_SCHEDULED);
                    meeting.setRecallScheduledAt(Instant.now());
                }
                meeting.setRecallError(null);
                meetingStore.save(meeting);
            } else {
                meetingStore.save(meeting);
                scheduleBot(meeting);
            }
        }
    }

    private String resolveEventId(Meeting meeting) {
        String calendarId = credentialStore.findConnected()
                .map(GoogleCredential::getRecallCalendarId).orElse(null);
        if (calendarId == null || meeting.getCalendarIcalUid() == null) {
            return null;
        }
        for (JsonNode event : calendarClient.findEventsByIcalUid(calendarId, meeting.getCalendarIcalUid())) {
            if (!event.path("is_deleted").asBoolean(false)) {
                return event.path("id").asString(null);
            }
        }
        return null;
    }

    private Map<String, String> metadata(Meeting meeting) {
        return Map.of(
                "meeting_id", String.valueOf(meeting.getId()),
                "lead_id", String.valueOf(meeting.getLeadId()),
                "lead_entity", String.valueOf(meeting.getLeadEntity()));
    }

    private boolean notYetJoined(String status) {
        return status == null || STATUS_PENDING.equals(status) || STATUS_FAILED.equals(status)
                || STATUS_SCHEDULED.equals(status);
    }

    private String firstBotId(JsonNode event) {
        for (JsonNode bot : event.path("bots")) {
            String id = bot.path("bot_id").asString(null);
            if (id == null || id.isBlank()) {
                id = bot.path("id").asString(null);
            }
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return null;
    }

    private String trim(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
