package com.leadrat.aisdk.meeting;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MeetingDiscussionProvider {

    private static final Logger log = LoggerFactory.getLogger(MeetingDiscussionProvider.class);

    private final AiSdkProperties properties;
    private final MeetingStore meetingStore;

    public MeetingDiscussionProvider(AiSdkProperties properties, MeetingStore meetingStore) {
        this.properties = properties;
        this.meetingStore = meetingStore;
    }

    public boolean enabled() {
        return properties.getMeeting().isActive();
    }

    public List<Map<String, Object>> forTarget(String entity, Object id) {
        if (!enabled() || id == null) {
            return List.of();
        }
        AiSdkProperties.Meeting meeting = properties.getMeeting();
        List<Discussion> discussions;
        try {
            discussions = meetingStore.discussionsForLead(String.valueOf(id), meeting.getMaxDiscussionsPerLead());
        } catch (RuntimeException e) {
            log.warn("ai-sdk: meeting discussion lookup failed for {}:{} ({})", entity, id, e.getMessage());
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Discussion discussion : discussions) {
            if (discussion.leadEntity() != null && entity != null
                    && !discussion.leadEntity().equalsIgnoreCase(entity)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("meetingId", discussion.meetingId());
            row.put("meetingTitle", discussion.meetingTitle());
            row.put("occurredAt", discussion.occurredAt() == null ? null : discussion.occurredAt().toString());
            row.put("participants", discussion.participants());
            row.put("discussion", truncate(discussion.discussion(), meeting.getDiscussionCharLimit()));
            out.add(row);
        }
        return out;
    }

    private String truncate(String text, int limit) {
        if (text == null || limit <= 0 || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "\n… transcript truncated …";
    }
}
