package com.leadrat.aisdk.meeting;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecallPayloadMapper {

    public record TranscriptText(String discussion, List<Map<String, String>> participants) {
    }

    public TranscriptText toDiscussion(JsonNode segments) {
        List<Map<String, String>> speakers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder transcript = new StringBuilder();
        if (segments != null && segments.isArray()) {
            for (JsonNode segment : segments) {
                String name = segment.path("participant").path("name").asString(null);
                String email = segment.path("participant").path("email").asString(null);
                StringBuilder text = new StringBuilder();
                for (JsonNode word : segment.path("words")) {
                    String w = word.path("text").asString(null);
                    if (w != null && !w.isBlank()) {
                        if (text.length() > 0) {
                            text.append(" ");
                        }
                        text.append(w);
                    }
                }
                if (text.length() == 0) {
                    continue;
                }
                String label = name != null && !name.isBlank() ? name : "Speaker";
                transcript.append(label).append(": ").append(text).append("\n");
                String key = label + "|" + (email == null ? "" : email);
                if (seen.add(key)) {
                    Map<String, String> speaker = new LinkedHashMap<>();
                    speaker.put("name", label);
                    if (email != null && !email.isBlank()) {
                        speaker.put("email", email);
                    }
                    speakers.add(speaker);
                }
            }
        }
        String full = transcript.toString().trim();
        return new TranscriptText(full.isEmpty() ? null : full, speakers);
    }
}
