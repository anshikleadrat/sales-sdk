package com.leadrat.aisdk.meeting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MeetingStore {

    private static final String COLUMNS = """
            id, lead_entity, lead_id, title, agenda, scheduled_at, duration_minutes, timezone, attendees,
            reminder_minutes, external_ref, meeting_link, conference_id, calendar_event_id, calendar_ical_uid,
            calendar_sync_status, calendar_sync_error, calendar_synced_at, recall_calendar_event_id,
            recall_bot_id, recall_bot_status, recall_scheduled_at, recall_error, status, created_at,
            updated_at""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MeetingStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<Meeting> meetingMapper = (rs, rowNum) -> {
        Meeting meeting = new Meeting();
        meeting.setId(rs.getString("id"));
        meeting.setLeadEntity(rs.getString("lead_entity"));
        meeting.setLeadId(rs.getString("lead_id"));
        meeting.setTitle(rs.getString("title"));
        meeting.setAgenda(rs.getString("agenda"));
        meeting.setScheduledAt(instant(rs, "scheduled_at"));
        meeting.setDurationMinutes(rs.getInt("duration_minutes"));
        meeting.setTimezone(rs.getString("timezone"));
        meeting.setAttendees(readAttendees(rs.getString("attendees")));
        meeting.setReminderMinutes(readIntList(rs.getString("reminder_minutes")));
        meeting.setExternalRef(rs.getString("external_ref"));
        meeting.setMeetingLink(rs.getString("meeting_link"));
        meeting.setConferenceId(rs.getString("conference_id"));
        meeting.setCalendarEventId(rs.getString("calendar_event_id"));
        meeting.setCalendarIcalUid(rs.getString("calendar_ical_uid"));
        meeting.setCalendarSyncStatus(rs.getString("calendar_sync_status"));
        meeting.setCalendarSyncError(rs.getString("calendar_sync_error"));
        meeting.setCalendarSyncedAt(instant(rs, "calendar_synced_at"));
        meeting.setRecallCalendarEventId(rs.getString("recall_calendar_event_id"));
        meeting.setRecallBotId(rs.getString("recall_bot_id"));
        meeting.setRecallBotStatus(rs.getString("recall_bot_status"));
        meeting.setRecallScheduledAt(instant(rs, "recall_scheduled_at"));
        meeting.setRecallError(rs.getString("recall_error"));
        meeting.setStatus(rs.getString("status"));
        meeting.setCreatedAt(instant(rs, "created_at"));
        meeting.setUpdatedAt(instant(rs, "updated_at"));
        return meeting;
    };

    public void save(Meeting meeting) {
        meeting.setUpdatedAt(Instant.now());
        if (meeting.getCreatedAt() == null) {
            meeting.setCreatedAt(meeting.getUpdatedAt());
        }
        jdbc.update("""
                INSERT INTO meeting (id, lead_entity, lead_id, title, agenda, scheduled_at, duration_minutes,
                    timezone, attendees, reminder_minutes, external_ref,
                    meeting_link, conference_id, calendar_event_id, calendar_ical_uid, calendar_sync_status,
                    calendar_sync_error, calendar_synced_at, recall_calendar_event_id, recall_bot_id,
                    recall_bot_status, recall_scheduled_at, recall_error, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    lead_entity = excluded.lead_entity,
                    lead_id = excluded.lead_id,
                    title = excluded.title,
                    agenda = excluded.agenda,
                    scheduled_at = excluded.scheduled_at,
                    duration_minutes = excluded.duration_minutes,
                    timezone = excluded.timezone,
                    attendees = excluded.attendees,
                    reminder_minutes = excluded.reminder_minutes,
                    external_ref = excluded.external_ref,
                    meeting_link = excluded.meeting_link,
                    conference_id = excluded.conference_id,
                    calendar_event_id = excluded.calendar_event_id,
                    calendar_ical_uid = excluded.calendar_ical_uid,
                    calendar_sync_status = excluded.calendar_sync_status,
                    calendar_sync_error = excluded.calendar_sync_error,
                    calendar_synced_at = excluded.calendar_synced_at,
                    recall_calendar_event_id = excluded.recall_calendar_event_id,
                    recall_bot_id = excluded.recall_bot_id,
                    recall_bot_status = excluded.recall_bot_status,
                    recall_scheduled_at = excluded.recall_scheduled_at,
                    recall_error = excluded.recall_error,
                    status = excluded.status,
                    updated_at = excluded.updated_at""",
                meeting.getId(), meeting.getLeadEntity(), meeting.getLeadId(), meeting.getTitle(),
                meeting.getAgenda(), text(meeting.getScheduledAt()), meeting.getDurationMinutes(),
                meeting.getTimezone(), writeJson(meeting.getAttendees()), writeJson(meeting.getReminderMinutes()),
                meeting.getExternalRef(),
                meeting.getMeetingLink(), meeting.getConferenceId(), meeting.getCalendarEventId(),
                meeting.getCalendarIcalUid(), meeting.getCalendarSyncStatus(), meeting.getCalendarSyncError(),
                text(meeting.getCalendarSyncedAt()), meeting.getRecallCalendarEventId(), meeting.getRecallBotId(),
                meeting.getRecallBotStatus(), text(meeting.getRecallScheduledAt()), meeting.getRecallError(),
                meeting.getStatus(), text(meeting.getCreatedAt()), text(meeting.getUpdatedAt()));
    }

    public Optional<Meeting> find(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM meeting WHERE id = ?", meetingMapper, id)
                .stream().findFirst();
    }

    public Optional<Meeting> findByBotId(String botId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM meeting WHERE recall_bot_id = ?", meetingMapper, botId)
                .stream().findFirst();
    }

    public List<Meeting> findByIcalUid(String icalUid) {
        return jdbc.query("SELECT " + COLUMNS + " FROM meeting WHERE calendar_ical_uid = ?", meetingMapper, icalUid);
    }

    public List<Meeting> findByLead(String leadId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM meeting WHERE lead_id = ? ORDER BY scheduled_at DESC",
                meetingMapper, leadId);
    }

    public List<Meeting> findPending(Instant after, int limit) {
        return jdbc.query("""
                SELECT %s FROM meeting
                WHERE status = 'SCHEDULED' AND scheduled_at > ?
                  AND ((recall_calendar_event_id IS NULL AND calendar_ical_uid IS NOT NULL)
                       OR (recall_bot_id IS NULL AND recall_calendar_event_id IS NOT NULL))
                ORDER BY scheduled_at ASC LIMIT ?""".formatted(COLUMNS),
                meetingMapper, text(after), limit);
    }

    public void saveDiscussion(Discussion discussion) {
        jdbc.update("""
                INSERT INTO meeting_discussion (meeting_id, lead_entity, lead_id, provider, external_id,
                    meeting_title, meeting_url, discussion, participants, started_at, ended_at, occurred_at,
                    received_at, match_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(provider, external_id) DO UPDATE SET
                    meeting_id = excluded.meeting_id,
                    lead_entity = excluded.lead_entity,
                    lead_id = excluded.lead_id,
                    meeting_title = excluded.meeting_title,
                    meeting_url = excluded.meeting_url,
                    discussion = COALESCE(excluded.discussion, meeting_discussion.discussion),
                    participants = COALESCE(excluded.participants, meeting_discussion.participants),
                    started_at = COALESCE(excluded.started_at, meeting_discussion.started_at),
                    ended_at = COALESCE(excluded.ended_at, meeting_discussion.ended_at),
                    occurred_at = excluded.occurred_at,
                    received_at = excluded.received_at,
                    match_status = excluded.match_status""",
                discussion.meetingId(), discussion.leadEntity(), discussion.leadId(), discussion.provider(),
                discussion.externalId(), discussion.meetingTitle(), discussion.meetingUrl(), discussion.discussion(),
                writeJson(discussion.participants()), text(discussion.startedAt()), text(discussion.endedAt()),
                text(discussion.occurredAt()), text(discussion.receivedAt()), discussion.matchStatus());
    }

    public Optional<Discussion> findDiscussion(String provider, String externalId) {
        return jdbc.query("SELECT * FROM meeting_discussion WHERE provider = ? AND external_id = ?",
                discussionMapper, provider, externalId).stream().findFirst();
    }

    public List<Discussion> discussionsForLead(String leadId, int limit) {
        return jdbc.query("""
                SELECT * FROM meeting_discussion
                WHERE lead_id = ? AND discussion IS NOT NULL
                ORDER BY occurred_at DESC, id DESC LIMIT ?""", discussionMapper, leadId, limit);
    }

    public List<Discussion> discussionsForMeeting(String meetingId) {
        return jdbc.query("SELECT * FROM meeting_discussion WHERE meeting_id = ? ORDER BY occurred_at DESC, id DESC",
                discussionMapper, meetingId);
    }

    private final RowMapper<Discussion> discussionMapper = (rs, rowNum) -> new Discussion(
            rs.getLong("id"),
            rs.getString("meeting_id"),
            rs.getString("lead_entity"),
            rs.getString("lead_id"),
            rs.getString("provider"),
            rs.getString("external_id"),
            rs.getString("meeting_title"),
            rs.getString("meeting_url"),
            rs.getString("discussion"),
            readParticipants(rs.getString("participants")),
            instant(rs, "started_at"),
            instant(rs, "ended_at"),
            instant(rs, "occurred_at"),
            instant(rs, "received_at"),
            rs.getString("match_status"));

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<Attendee> readAttendees(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Attendee.class));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<Integer> readIntList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, Integer.class));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> readParticipants(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
