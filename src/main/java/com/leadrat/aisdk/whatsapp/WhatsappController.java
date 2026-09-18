package com.leadrat.aisdk.whatsapp;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai-sdk/whatsapp")
public class WhatsappController {

    private final AiSdkProperties properties;
    private final EngagetoHttp http;
    private final EngagetoClient client;
    private final WhatsappChatService chatService;
    private final LeadPhoneResolver phoneResolver;

    public WhatsappController(AiSdkProperties properties, EngagetoHttp http, EngagetoClient client,
                              WhatsappChatService chatService, LeadPhoneResolver phoneResolver) {
        this.properties = properties;
        this.http = http;
        this.client = client;
        this.chatService = chatService;
        this.phoneResolver = phoneResolver;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", properties.getWhatsapp().isActive());
        body.put("configured", http.configured());
        body.put("baseUrl", properties.getWhatsapp().getBaseUrl());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/chats")
    public ResponseEntity<?> chats(@RequestParam(required = false) String phone,
                                   @RequestParam(required = false) String entity,
                                   @RequestParam(required = false) String leadId,
                                   @RequestParam(required = false) Integer limit) {
        String resolved = phoneResolver.resolve(phone, null);
        if (resolved == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "a phone number is required"));
        }
        int cap = limit == null || limit <= 0 ? properties.getWhatsapp().getMaxMessagesPerLead() : limit;
        List<WhatsappMessage> messages = chatService.forLead(resolved, entity, leadId);
        return ResponseEntity.ok(messages.stream().limit(cap).toList());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestParam String phone,
                                     @RequestParam(required = false) String entity,
                                     @RequestParam(required = false) String leadId) {
        String resolved = phoneResolver.resolve(phone, null);
        if (resolved == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "a phone number is required"));
        }
        chatService.sync(resolved, entity, leadId);
        return ResponseEntity.ok(Map.of("phone", resolved));
    }

    @GetMapping("/preview")
    public ResponseEntity<?> preview(@RequestParam String phone,
                                     @RequestParam(required = false, defaultValue = "1") int page) {
        if (!http.configured()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "whatsapp is not configured"));
        }
        String resolved = phoneResolver.resolve(phone, null);
        try {
            JsonNode raw = client.messages(resolved, page, client.pageSize());
            return ResponseEntity.ok(Map.of("phone", resolved, "raw", raw));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        }
    }
}
