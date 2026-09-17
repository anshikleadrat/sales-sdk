package com.leadrat.aisdk.meeting;

import java.time.Instant;

public class Meeting {

    private String id;
    private String leadEntity;
    private String leadId;
    private String title;
    private String agenda;
    private Instant scheduledAt;
    private int durationMinutes = 60;
    private String meetingLink;
    private String conferenceId;
    private String calendarEventId;
    private String calendarIcalUid;
    private String calendarSyncStatus;
    private String calendarSyncError;
    private Instant calendarSyncedAt;
    private String recallCalendarEventId;
    private String recallBotId;
    private String recallBotStatus;
    private Instant recallScheduledAt;
    private String recallError;
    private String status = MeetingStatus.SCHEDULED;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLeadEntity() { return leadEntity; }
    public void setLeadEntity(String leadEntity) { this.leadEntity = leadEntity; }
    public String getLeadId() { return leadId; }
    public void setLeadId(String leadId) { this.leadId = leadId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }
    public String getConferenceId() { return conferenceId; }
    public void setConferenceId(String conferenceId) { this.conferenceId = conferenceId; }
    public String getCalendarEventId() { return calendarEventId; }
    public void setCalendarEventId(String calendarEventId) { this.calendarEventId = calendarEventId; }
    public String getCalendarIcalUid() { return calendarIcalUid; }
    public void setCalendarIcalUid(String calendarIcalUid) { this.calendarIcalUid = calendarIcalUid; }
    public String getCalendarSyncStatus() { return calendarSyncStatus; }
    public void setCalendarSyncStatus(String calendarSyncStatus) { this.calendarSyncStatus = calendarSyncStatus; }
    public String getCalendarSyncError() { return calendarSyncError; }
    public void setCalendarSyncError(String calendarSyncError) { this.calendarSyncError = calendarSyncError; }
    public Instant getCalendarSyncedAt() { return calendarSyncedAt; }
    public void setCalendarSyncedAt(Instant calendarSyncedAt) { this.calendarSyncedAt = calendarSyncedAt; }
    public String getRecallCalendarEventId() { return recallCalendarEventId; }
    public void setRecallCalendarEventId(String recallCalendarEventId) { this.recallCalendarEventId = recallCalendarEventId; }
    public String getRecallBotId() { return recallBotId; }
    public void setRecallBotId(String recallBotId) { this.recallBotId = recallBotId; }
    public String getRecallBotStatus() { return recallBotStatus; }
    public void setRecallBotStatus(String recallBotStatus) { this.recallBotStatus = recallBotStatus; }
    public Instant getRecallScheduledAt() { return recallScheduledAt; }
    public void setRecallScheduledAt(Instant recallScheduledAt) { this.recallScheduledAt = recallScheduledAt; }
    public String getRecallError() { return recallError; }
    public void setRecallError(String recallError) { this.recallError = recallError; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isOpen() {
        return MeetingStatus.SCHEDULED.equals(status);
    }
}
