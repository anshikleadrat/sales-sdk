package com.leadrat.aisdk.whatsapp;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WhatsappChatService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappChatService.class);

    private final EngagetoClient client;
    private final EngagetoHttp http;
    private final WhatsappPayloadMapper mapper;
    private final WhatsappStore store;
    private final AiSdkProperties properties;

    public WhatsappChatService(EngagetoClient client, EngagetoHttp http, WhatsappPayloadMapper mapper,
                               WhatsappStore store, AiSdkProperties properties) {
        this.client = client;
        this.http = http;
        this.mapper = mapper;
        this.store = store;
        this.properties = properties;
    }

    public List<WhatsappMessage> forLead(String phone, String leadEntity, String leadId) {
        if (phone == null || phone.isBlank() || !http.configured()) {
            return List.of();
        }
        sync(phone, leadEntity, leadId);
        return store.forPhone(phone, properties.getWhatsapp().getMaxMessagesPerLead());
    }

    public void sync(String phone, String leadEntity, String leadId) {
        Duration ttl = Duration.ofMinutes(properties.getWhatsapp().getCacheTtlMinutes());
        Instant lastFetch = store.lastFetchedAt(phone).orElse(null);
        if (lastFetch != null && lastFetch.plus(ttl).isAfter(Instant.now())) {
            return;
        }
        Set<Integer> inboundStatuses = parseStatuses(properties.getWhatsapp().getInboundStatuses());
        int messageCount = 0;
        try {
            int pages = Math.max(1, client.maxPages());
            for (int page = 1; page <= pages; page++) {
                JsonNode root = client.messages(phone, page, client.pageSize());
                List<WhatsappMessage> batch = mapper.toMessages(root, phone, inboundStatuses);
                for (WhatsappMessage message : batch) {
                    store.upsert(withLead(message, leadEntity, leadId));
                }
                messageCount += batch.size();
                if (page >= mapper.totalPages(root)) {
                    break;
                }
            }
            store.recordSync(phone, Instant.now(), messageCount, null);
        } catch (RuntimeException e) {
            log.warn("ai-sdk: whatsapp sync failed for {} ({})", phone, e.getMessage());
            store.recordSync(phone, null, messageCount, e.getMessage());
        }
    }

    private WhatsappMessage withLead(WhatsappMessage message, String leadEntity, String leadId) {
        if (leadEntity == null && leadId == null) {
            return message;
        }
        return new WhatsappMessage(message.id(), message.phone(), leadEntity, leadId, message.externalId(),
                message.direction(), message.status(), message.senderName(), message.body(), message.mediaType(),
                message.templateName(), message.sentAt(), message.fetchedAt());
    }

    private Set<Integer> parseStatuses(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toSet());
    }
}
