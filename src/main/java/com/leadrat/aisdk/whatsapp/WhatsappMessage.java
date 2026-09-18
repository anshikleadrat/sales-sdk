package com.leadrat.aisdk.whatsapp;

import java.time.Instant;

public record WhatsappMessage(
        long id,
        String phone,
        String leadEntity,
        String leadId,
        String externalId,
        String direction,
        Integer status,
        String senderName,
        String body,
        String mediaType,
        String templateName,
        Instant sentAt,
        Instant fetchedAt) {

    public static final String INBOUND = "INBOUND";
    public static final String OUTBOUND = "OUTBOUND";
}
