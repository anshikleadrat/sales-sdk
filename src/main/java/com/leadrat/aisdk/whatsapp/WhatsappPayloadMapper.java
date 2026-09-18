package com.leadrat.aisdk.whatsapp;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WhatsappPayloadMapper {

    public List<WhatsappMessage> toMessages(JsonNode root, String phone, Set<Integer> inboundStatuses) {
        List<WhatsappMessage> out = new ArrayList<>();
        Instant fetchedAt = Instant.now();
        for (JsonNode item : root.path("data")) {
            String externalId = item.path("id").asString(null);
            if (externalId == null || externalId.isBlank()) {
                continue;
            }
            Integer status = item.path("status").isNumber() ? item.path("status").asInt() : null;
            String direction = status != null && inboundStatuses.contains(status)
                    ? WhatsappMessage.INBOUND : WhatsappMessage.OUTBOUND;
            String text = firstNonBlank(item.path("textMessage").asString(null),
                    item.path("mediaCaption").asString(null));
            String mediaType = item.path("mediaMimeType").asString(null);
            if (text == null && mediaType != null && !mediaType.isBlank()) {
                text = "[" + mediaType + "]";
            }
            String buttons = formatButtons(item.path("buttons"));
            if (buttons != null) {
                text = text == null ? buttons : text + " " + buttons;
            }
            Instant sentAt = parseSentAt(item.path("createdAt").asString(null), fetchedAt);
            out.add(new WhatsappMessage(0, phone, null, null, externalId, direction, status,
                    item.path("contactName").asString(null), text, mediaType,
                    item.path("templateName").asString(null), sentAt, fetchedAt));
        }
        return out;
    }

    public int totalPages(JsonNode root) {
        return root.path("totalPages").isNumber() ? root.path("totalPages").asInt() : 1;
    }

    private String formatButtons(JsonNode buttons) {
        if (buttons == null || !buttons.isArray() || buttons.isEmpty()) {
            return null;
        }
        List<String> labels = new ArrayList<>();
        buttons.forEach(b -> {
            String label = b.isTextual() ? b.asString(null) : b.path("text").asString(null);
            if (label != null && !label.isBlank()) {
                labels.add(label);
            }
        });
        return labels.isEmpty() ? null : "[buttons: " + String.join(", ", labels) + "]";
    }

    private Instant parseSentAt(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException e2) {
                return fallback;
            }
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }
}
