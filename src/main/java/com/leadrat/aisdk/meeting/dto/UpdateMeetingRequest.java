package com.leadrat.aisdk.meeting.dto;

import java.time.Instant;

public record UpdateMeetingRequest(
        String title,
        String agenda,
        Instant scheduledAt,
        Integer durationMinutes) {
}
