package com.leadrat.aisdk.meeting.dto;

import com.leadrat.aisdk.meeting.Attendee;

import java.time.Instant;
import java.util.List;

public record UpdateMeetingRequest(
        String title,
        String agenda,
        Instant scheduledAt,
        Integer durationMinutes,
        String timezone,
        List<Attendee> attendees,
        List<Integer> reminderMinutes) {
}
