package com.leadrat.aisdk.whatsapp;

import com.leadrat.aisdk.config.AiSdkProperties;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

public class EngagetoClient {

    private static final String MESSAGES_PATH = "/api/get-waMessage";

    private final EngagetoHttp http;
    private final AiSdkProperties properties;

    public EngagetoClient(EngagetoHttp http, AiSdkProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public JsonNode messages(String phone, int pageNumber, int pageSize) {
        String uri = UriComponentsBuilder.fromPath(MESSAGES_PATH)
                .queryParam("phoneNumber", phone)
                .queryParam("pageNumber", pageNumber)
                .queryParam("pageSize", pageSize)
                .build().toUriString();
        String raw = http.client().get().uri(uri).retrieve().body(String.class);
        return http.parse(raw, "Engageto get-waMessage");
    }

    public int pageSize() {
        return properties.getWhatsapp().getPageSize();
    }

    public int maxPages() {
        return properties.getWhatsapp().getMaxPages();
    }
}
