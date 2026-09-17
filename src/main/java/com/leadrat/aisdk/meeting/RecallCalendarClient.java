package com.leadrat.aisdk.meeting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecallCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(RecallCalendarClient.class);
    private static final String CALENDARS = "/api/v2/calendars/";
    private static final String EVENTS = "/api/v2/calendar-events/";

    private final RecallHttp http;

    public RecallCalendarClient(RecallHttp http) {
        this.http = http;
    }

    public JsonNode createCalendar(String clientId, String clientSecret, String refreshToken,
                                   String oauthEmail, Map<String, String> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("platform", "google_calendar");
        body.put("oauth_client_id", clientId);
        body.put("oauth_client_secret", clientSecret);
        body.put("oauth_refresh_token", refreshToken);
        if (oauthEmail != null && !oauthEmail.isBlank()) {
            body.put("oauth_email", oauthEmail);
        }
        body.put("metadata", metadata);
        String raw = http.client().post().uri(CALENDARS).body(body).retrieve().body(String.class);
        JsonNode node = http.parse(raw, "Recall create calendar");
        http.requireId(node, "Recall create calendar");
        return node;
    }

    public JsonNode retrieveCalendar(String calendarId) {
        String raw = http.client().get().uri(CALENDARS + "{id}/", calendarId).retrieve().body(String.class);
        return http.parse(raw, "Recall retrieve calendar");
    }

    public void deleteCalendar(String calendarId) {
        try {
            http.client().delete().uri(CALENDARS + "{id}/", calendarId).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("ai-sdk: recall delete calendar {} failed ({})", calendarId, e.getMessage());
        }
    }

    public List<JsonNode> findEventsByIcalUid(String calendarId, String icalUid) {
        return listEvents(UriComponentsBuilder.fromPath(EVENTS)
                .queryParam("calendar_id", calendarId)
                .queryParam("ical_uid", icalUid)
                .build().toUriString());
    }

    public List<JsonNode> findEventsUpdatedSince(String calendarId, Instant updatedSince) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(EVENTS)
                .queryParam("calendar_id", calendarId);
        if (updatedSince != null) {
            builder.queryParam("updated_at__gte", updatedSince.toString());
        }
        return listEvents(builder.build().toUriString());
    }

    public JsonNode scheduleBot(String calendarEventId, String deduplicationKey, Map<String, Object> botConfig) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deduplication_key", deduplicationKey);
        body.put("bot_config", botConfig);
        String raw = http.client().post().uri(EVENTS + "{id}/bot/", calendarEventId)
                .body(body).retrieve().body(String.class);
        return http.parse(raw, "Recall schedule bot");
    }

    public void unscheduleBot(String calendarEventId) {
        try {
            http.client().delete().uri(EVENTS + "{id}/bot/", calendarEventId).retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("ai-sdk: recall unschedule bot for event {} failed ({})", calendarEventId, e.getMessage());
        }
    }

    private List<JsonNode> listEvents(String uri) {
        List<JsonNode> all = new ArrayList<>();
        String next = uri;
        for (int page = 0; next != null && page < 20; page++) {
            String raw = next.startsWith("http")
                    ? http.client().get().uri(URI.create(next)).retrieve().body(String.class)
                    : http.client().get().uri(next).retrieve().body(String.class);
            JsonNode node = http.parse(raw, "Recall list calendar events");
            for (JsonNode event : node.path("results")) {
                all.add(event);
            }
            next = node.path("next").asString(null);
        }
        return all;
    }
}
