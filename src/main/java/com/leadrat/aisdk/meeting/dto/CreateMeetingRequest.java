package com.leadrat.aisdk.meeting.dto;

import java.time.Instant;

public record CreateMeetingRequest(
        String leadEntity,
        String leadId,
        String title,
        String agenda,
        Instant scheduledAt,
        Integer durationMinutes) {
}
