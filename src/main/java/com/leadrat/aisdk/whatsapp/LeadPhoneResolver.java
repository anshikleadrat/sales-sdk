package com.leadrat.aisdk.whatsapp;

import com.leadrat.aisdk.config.AiSdkProperties;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LeadPhoneResolver {

    private final AiSdkProperties properties;

    public LeadPhoneResolver(AiSdkProperties properties) {
        this.properties = properties;
    }

    public String resolve(String explicitPhone, Map<String, Object> self) {
        String candidate = explicitPhone;
        if (candidate == null || candidate.isBlank()) {
            candidate = fromSelf(self);
        }
        return normalize(candidate);
    }

    private String fromSelf(Map<String, Object> self) {
        if (self == null || self.isEmpty()) {
            return null;
        }
        for (String field : phoneFields()) {
            for (Map.Entry<String, Object> entry : self.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(field) && entry.getValue() != null) {
                    String value = String.valueOf(entry.getValue());
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private List<String> phoneFields() {
        return Arrays.stream(properties.getWhatsapp().getPhoneFields().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (raw.trim().startsWith("00")) {
            digits = digits.substring(2);
        }
        String countryCode = properties.getWhatsapp().getDefaultCountryCode();
        if (digits.length() == 10) {
            digits = countryCode + digits;
        } else if (digits.startsWith("0") && digits.length() == 11) {
            digits = countryCode + digits.substring(1);
        }
        return digits;
    }
}
