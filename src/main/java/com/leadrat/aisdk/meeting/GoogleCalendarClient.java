package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class GoogleCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarClient.class);
    private static final String EVENTS = "https://www.googleapis.com/calendar/v3/calendars/primary/events";

    private final AiSdkProperties properties;
    private final GoogleTokenStore tokenStore;

    public GoogleCalendarClient(AiSdkProperties properties, GoogleTokenStore tokenStore) {
        this.properties = properties;
        this.tokenStore = tokenStore;
    }

    public record CalendarSyncResult(
            String eventId,
            String icalUid,
            String meetingLink,
            String conferenceId,
            String status,
            String error,
            Instant syncedAt) {
    }

    public boolean enabled() {
        AiSdkProperties.Google google = properties.getMeeting().getGoogle();
        return google.isActive() && google.getClientId() != null && !google.getClientId().isBlank();
    }

    public boolean canGenerateLinks() {
        return enabled() && tokenStore.connected();
    }

    public CalendarSyncResult createEvent(Meeting meeting) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = RestClient.create()
                    .post()
                    .uri(EVENTS + "?conferenceDataVersion=1")
                    .header("Authorization", "Bearer " + tokenStore.accessToken())
                    .body(eventBody(meeting))
                    .retrieve()
                    .body(Map.class);
            return applyResult(response);
        } catch (Exception e) {
            log.warn("ai-sdk: google calendar create failed for meeting {} ({})", meeting.getId(), e.getMessage());
            return failure(meeting, e.getMessage());
        }
    }

    public CalendarSyncResult updateEvent(Meeting meeting) {
        if (meeting.getCalendarEventId() == null) {
            return createEvent(meeting);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = RestClient.create()
                    .patch()
                    .uri(EVENTS + "/{id}?conferenceDataVersion=1", meeting.getCalendarEventId())
                    .header("Authorization", "Bearer " + tokenStore.accessToken())
                    .body(eventBody(meeting))
                    .retrieve()
                    .body(Map.class);
            return applyResult(response);
        } catch (Exception e) {
            log.warn("ai-sdk: google calendar update failed for meeting {} ({})", meeting.getId(), e.getMessage());
            return failure(meeting, e.getMessage());
        }
    }

    public void deleteEvent(Meeting meeting) {
        if (meeting.getCalendarEventId() == null) {
            return;
        }
        try {
            RestClient.create()
                    .delete()
                    .uri(EVENTS + "/{id}", meeting.getCalendarEventId())
                    .header("Authorization", "Bearer " + tokenStore.accessToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("ai-sdk: google calendar delete failed for meeting {} ({})", meeting.getId(), e.getMessage());
        }
    }

    private CalendarSyncResult applyResult(Map<String, Object> response) {
        if (response == null) {
            return new CalendarSyncResult(null, null, null, null, "FAILED", "empty response", Instant.now());
        }
        String eventId = (String) response.get("id");
        String icalUid = (String) response.get("iCalUID");
        String hangoutLink = (String) response.get("hangoutLink");
        String conferenceId = null;
        Object conferenceData = response.get("conferenceData");
        if (conferenceData instanceof Map<?, ?> cd) {
            Object cid = cd.get("conferenceId");
            if (cid != null) {
                conferenceId = String.valueOf(cid);
            }
        }
        if (hangoutLink == null && conferenceData instanceof Map<?, ?> cd
                && cd.get("entryPoints") instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> ep && "video".equals(ep.get("entryPointType"))) {
                    hangoutLink = String.valueOf(ep.get("uri"));
                    break;
                }
            }
        }
        return new CalendarSyncResult(eventId, icalUid, hangoutLink, conferenceId, "SYNCED", null, Instant.now());
    }

    private CalendarSyncResult failure(Meeting meeting, String error) {
        return new CalendarSyncResult(meeting.getCalendarEventId(), meeting.getCalendarIcalUid(),
                meeting.getMeetingLink(), meeting.getConferenceId(), "FAILED", trim(error), Instant.now());
    }

    private Map<String, Object> eventBody(Meeting meeting) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        String start = meeting.getScheduledAt().atOffset(ZoneOffset.UTC).format(fmt);
        String end = meeting.getScheduledAt().plusSeconds(Math.max(15, meeting.getDurationMinutes()) * 60L)
                .atOffset(ZoneOffset.UTC).format(fmt);
        return Map.of(
                "summary", meeting.getTitle() == null || meeting.getTitle().isBlank()
                        ? "Meeting" : meeting.getTitle(),
                "description", meeting.getAgenda() == null ? "" : meeting.getAgenda(),
                "start", Map.of("dateTime", start),
                "end", Map.of("dateTime", end),
                "conferenceData", Map.of("createRequest", Map.of(
                        "requestId", meeting.getId(),
                        "conferenceSolutionKey", Map.of("type", "hangoutsMeet"))));
    }

    private String trim(String s) {
        if (s == null) {
            return "sync failed";
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
