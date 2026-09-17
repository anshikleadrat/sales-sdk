package com.leadrat.aisdk.meeting.dto;

import com.leadrat.aisdk.meeting.Meeting;

import java.time.Instant;

public record MeetingResponse(
        String id,
        String leadEntity,
        String leadId,
        String title,
        String agenda,
        Instant scheduledAt,
        int durationMinutes,
        String meetingLink,
        String status,
        String calendarSyncStatus,
        String calendarSyncError,
        String recallBotStatus,
        String recallError,
        Instant createdAt) {

    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(meeting.getId(), meeting.getLeadEntity(), meeting.getLeadId(),
                meeting.getTitle(), meeting.getAgenda(), meeting.getScheduledAt(), meeting.getDurationMinutes(),
                meeting.getMeetingLink(), meeting.getStatus(), meeting.getCalendarSyncStatus(),
                meeting.getCalendarSyncError(), meeting.getRecallBotStatus(), meeting.getRecallError(),
                meeting.getCreatedAt());
    }
}
