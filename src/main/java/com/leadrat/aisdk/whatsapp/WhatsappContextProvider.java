package com.leadrat.aisdk.whatsapp;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhatsappContextProvider {

    private static final Logger log = LoggerFactory.getLogger(WhatsappContextProvider.class);

    private final AiSdkProperties properties;
    private final WhatsappChatService chatService;
    private final LeadPhoneResolver phoneResolver;

    public WhatsappContextProvider(AiSdkProperties properties, WhatsappChatService chatService,
                                   LeadPhoneResolver phoneResolver) {
        this.properties = properties;
        this.chatService = chatService;
        this.phoneResolver = phoneResolver;
    }

    public boolean enabled() {
        return properties.getWhatsapp().isActive();
    }

    public List<Map<String, Object>> forTarget(String entity, Object id, String explicitPhone,
                                               Map<String, Object> self) {
        if (!enabled()) {
            return List.of();
        }
        String phone = phoneResolver.resolve(explicitPhone, self);
        if (phone == null) {
            return List.of();
        }
        List<WhatsappMessage> messages;
        try {
            messages = chatService.forLead(phone, entity, id == null ? null : String.valueOf(id));
        } catch (RuntimeException e) {
            log.warn("ai-sdk: whatsapp chat lookup failed for {}:{} ({})", entity, id, e.getMessage());
            return List.of();
        }
        int limit = properties.getWhatsapp().getChatCharLimit();
        int used = 0;
        List<Map<String, Object>> out = new ArrayList<>();
        for (WhatsappMessage message : messages) {
            String text = message.body();
            if (text == null) {
                continue;
            }
            if (limit > 0 && used + text.length() > limit) {
                break;
            }
            used += text.length();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sentAt", message.sentAt() == null ? null : message.sentAt().toString());
            row.put("direction", message.direction());
            row.put("sender", message.direction().equals(WhatsappMessage.INBOUND)
                    ? message.senderName() : "business");
            row.put("text", text);
            row.put("template", message.templateName());
            out.add(row);
        }
        return out;
    }
}
