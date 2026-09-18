package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.meeting.dto.CreateMeetingRequest;
import com.leadrat.aisdk.meeting.dto.UpdateMeetingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);

    private final AiSdkProperties properties;
    private final MeetingStore meetingStore;
    private final GoogleCalendarClient calendarClient;
    private final RecallService recallService;

    public MeetingService(AiSdkProperties properties, MeetingStore meetingStore,
                          GoogleCalendarClient calendarClient, RecallService recallService) {
        this.properties = properties;
        this.meetingStore = meetingStore;
        this.calendarClient = calendarClient;
        this.recallService = recallService;
    }

    public Meeting schedule(CreateMeetingRequest request) {
        if (!properties.getMeeting().isActive()) {
            throw new IllegalArgumentException("Meetings are off. Add a Google OAuth client id and secret on the "
                    + "SDK's keys page (/ai-sdk/settings) and the feature turns itself on.");
        }
        if (request == null || request.leadId() == null || request.leadId().isBlank()) {
            throw new IllegalArgumentException("leadId is required");
        }
        if (request.scheduledAt() != null && !request.scheduledAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Meeting time must be in the future");
        }
        if (!calendarClient.enabled()) {
            throw new IllegalArgumentException("Google Calendar is not configured. Add the OAuth client id and "
                    + "secret on the SDK's keys page (/ai-sdk/settings).");
        }
        if (!calendarClient.canGenerateLinks()) {
            throw new IllegalArgumentException(
                    "No Google account is connected — connect one on the SDK's meetings page first");
        }
        Meeting meeting = new Meeting();
        meeting.setId(UUID.randomUUID().toString());
        meeting.setLeadEntity(request.leadEntity() == null || request.leadEntity().isBlank()
                ? properties.getMeeting().getLeadEntity() : request.leadEntity());
        meeting.setLeadId(request.leadId());
        meeting.setTitle(request.title());
        meeting.setAgenda(request.agenda());
        meeting.setScheduledAt(request.scheduledAt() == null ? Instant.now() : request.scheduledAt());
        if (request.durationMinutes() != null && request.durationMinutes() > 0) {
            meeting.setDurationMinutes(request.durationMinutes());
        }
        meeting.setTimezone(request.timezone());
        meeting.setAttendees(request.attendees());
        meeting.setReminderMinutes(request.reminderMinutes());
        meeting.setExternalRef(request.externalRef());
        meeting.setStatus(MeetingStatus.SCHEDULED);
        meeting.setCalendarSyncStatus("PENDING");
        meetingStore.save(meeting);
        applyCalendarResult(meeting.getId(), true);

        Meeting created = meetingStore.find(meeting.getId()).orElse(meeting);
        if (created.getMeetingLink() == null || created.getMeetingLink().isBlank()) {
            throw new IllegalStateException(created.getCalendarSyncError() == null
                    ? "Google did not return a meeting link"
                    : "Google could not generate a meeting link: " + created.getCalendarSyncError());
        }
        return created;
    }

    public Meeting update(String meetingId, UpdateMeetingRequest request) {
        Meeting meeting = require(meetingId);
        if (!meeting.isOpen()) {
            throw new IllegalArgumentException("Only open meetings can be edited");
        }
        if (request.title() != null) {
            meeting.setTitle(request.title());
        }
        if (request.agenda() != null) {
            meeting.setAgenda(request.agenda());
        }
        if (request.scheduledAt() != null) {
            meeting.setScheduledAt(request.scheduledAt() == null ? Instant.now() : request.scheduledAt());
        }
        if (request.durationMinutes() != null && request.durationMinutes() > 0) {
            meeting.setDurationMinutes(request.durationMinutes());
        }
        if (request.timezone() != null) {
            meeting.setTimezone(request.timezone());
        }
        if (request.attendees() != null) {
            meeting.setAttendees(request.attendees());
        }
        if (request.reminderMinutes() != null) {
            meeting.setReminderMinutes(request.reminderMinutes());
        }
        meetingStore.save(meeting);
        if (meeting.getCalendarEventId() != null) {
            applyCalendarResult(meetingId, false);
        } else {
            recallService.scheduleBot(meetingId);
        }
        return require(meetingId);
    }

    public Meeting cancel(String meetingId) {
        Meeting meeting = require(meetingId);
        if (!meeting.isOpen()) {
            throw new IllegalArgumentException("Only open meetings can be cancelled");
        }
        meeting.setStatus(MeetingStatus.CANCELLED);
        meetingStore.save(meeting);
        recallService.removeBot(meetingId);
        if (calendarClient.enabled()) {
            calendarClient.deleteEvent(meeting);
        }
        return require(meetingId);
    }

    public Meeting complete(String meetingId) {
        Meeting meeting = require(meetingId);
        meeting.setStatus(MeetingStatus.COMPLETED);
        meetingStore.save(meeting);
        return meeting;
    }

    public List<Meeting> byLead(String leadId) {
        return meetingStore.findByLead(leadId);
    }

    public Optional<Meeting> find(String meetingId) {
        return meetingStore.find(meetingId);
    }

    public List<Discussion> discussions(String meetingId) {
        return meetingStore.discussionsForMeeting(meetingId);
    }

    public List<Discussion> leadDiscussions(String leadId, int limit) {
        return meetingStore.discussionsForLead(leadId, limit);
    }

    private Meeting require(String meetingId) {
        return meetingStore.find(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
    }

    private void applyCalendarResult(String meetingId, boolean create) {
        if (!calendarClient.canGenerateLinks()) {
            return;
        }
        try {
            Meeting meeting = meetingStore.find(meetingId).orElse(null);
            if (meeting == null) {
                return;
            }
            GoogleCalendarClient.CalendarSyncResult result = create
                    ? calendarClient.createEvent(meeting)
                    : calendarClient.updateEvent(meeting);
            if ("SYNCED".equals(result.status())) {
                meeting.setCalendarEventId(result.eventId());
                if (result.icalUid() != null) {
                    meeting.setCalendarIcalUid(result.icalUid());
                }
                if (result.meetingLink() != null) {
                    meeting.setMeetingLink(result.meetingLink());
                }
                meeting.setConferenceId(result.conferenceId());
                meeting.setCalendarSyncStatus("SYNCED");
                meeting.setCalendarSyncError(null);
                meeting.setCalendarSyncedAt(result.syncedAt());
            } else {
                meeting.setCalendarSyncStatus("FAILED");
                meeting.setCalendarSyncError(result.error());
            }
            meetingStore.save(meeting);
            if ("SYNCED".equals(result.status())) {
                recallService.scheduleBot(meetingId);
            }
        } catch (Exception e) {
            log.warn("ai-sdk: calendar sync failed for meeting {} ({})", meetingId, e.getMessage());
        }
    }
}
