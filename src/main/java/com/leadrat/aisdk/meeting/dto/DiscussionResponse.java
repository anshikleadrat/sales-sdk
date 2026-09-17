package com.leadrat.aisdk.meeting.dto;

import com.leadrat.aisdk.meeting.Discussion;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DiscussionResponse(
        long id,
        String meetingId,
        String leadEntity,
        String leadId,
        String meetingTitle,
        String meetingUrl,
        String discussion,
        List<Map<String, String>> participants,
        Instant occurredAt,
        Instant receivedAt,
        String matchStatus) {

    public static DiscussionResponse from(Discussion discussion) {
        return new DiscussionResponse(discussion.id(), discussion.meetingId(), discussion.leadEntity(),
                discussion.leadId(), discussion.meetingTitle(), discussion.meetingUrl(), discussion.discussion(),
                discussion.participants(), discussion.occurredAt(), discussion.receivedAt(),
                discussion.matchStatus());
    }
}
