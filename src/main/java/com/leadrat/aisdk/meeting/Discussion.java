package com.leadrat.aisdk.meeting;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record Discussion(
        long id,
        String meetingId,
        String leadEntity,
        String leadId,
        String provider,
        String externalId,
        String meetingTitle,
        String meetingUrl,
        String discussion,
        List<Map<String, String>> participants,
        Instant startedAt,
        Instant endedAt,
        Instant occurredAt,
        Instant receivedAt,
        String matchStatus) {
}
