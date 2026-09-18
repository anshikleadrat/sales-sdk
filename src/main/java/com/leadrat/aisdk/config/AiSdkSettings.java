package com.leadrat.aisdk.config;

import com.leadrat.aisdk.security.SecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.BiConsumer;

public class AiSdkSettings {

    private static final Logger log = LoggerFactory.getLogger(AiSdkSettings.class);
    private static final String GOOGLE_TOKEN_KEY = "google-token-key";

    public record Key(String name,
                      String label,
                      boolean secret,
                      Function<AiSdkProperties, String> reader,
                      BiConsumer<AiSdkProperties, String> writer) {
    }

    public static final List<Key> KEYS = List.of(
            new Key("llm.provider", "LLM provider", false,
                    p -> p.getLlm().getProvider(), (p, v) -> p.getLlm().setProvider(v)),
            new Key("llm.api-key", "OpenRouter API key", true,
                    p -> p.getLlm().getApiKey(), (p, v) -> p.getLlm().setApiKey(v)),
            new Key("llm.base-url", "OpenRouter base URL", false,
                    p -> p.getLlm().getBaseUrl(), (p, v) -> p.getLlm().setBaseUrl(v)),
            new Key("llm.planner-model", "Planner model", false,
                    p -> p.getLlm().getPlannerModel(), (p, v) -> p.getLlm().setPlannerModel(v)),
            new Key("llm.summarizer-model", "Summarizer model", false,
                    p -> p.getLlm().getSummarizerModel(), (p, v) -> p.getLlm().setSummarizerModel(v)),
            new Key("llm.timeout-seconds", "LLM request timeout (seconds)", false,
                    p -> String.valueOf(p.getLlm().getTimeoutSeconds()),
                    (p, v) -> p.getLlm().setTimeoutSeconds(positiveInt("llm.timeout-seconds", v))),
            new Key("meeting.lead-entity", "Lead entity", false,
                    p -> p.getMeeting().getLeadEntity(), (p, v) -> p.getMeeting().setLeadEntity(v)),
            new Key("meeting.google.client-id", "Google OAuth client id", false,
                    p -> p.getMeeting().getGoogle().getClientId(), (p, v) -> p.getMeeting().getGoogle().setClientId(v)),
            new Key("meeting.google.client-secret", "Google OAuth client secret", true,
                    p -> p.getMeeting().getGoogle().getClientSecret(), (p, v) -> p.getMeeting().getGoogle().setClientSecret(v)),
            new Key("meeting.google.redirect-uri", "Google redirect URI", false,
                    p -> p.getMeeting().getGoogle().getRedirectUri(), (p, v) -> p.getMeeting().getGoogle().setRedirectUri(v)),
            new Key("meeting.recall.api-key", "Recall.ai API key", true,
                    p -> p.getMeeting().getRecall().getApiKey(), (p, v) -> p.getMeeting().getRecall().setApiKey(v)),
            new Key("meeting.recall.webhook-secret", "Recall.ai webhook secret", true,
                    p -> p.getMeeting().getRecall().getWebhookSecret(), (p, v) -> p.getMeeting().getRecall().setWebhookSecret(v)),
            new Key("meeting.recall.base-url", "Recall.ai base URL", false,
                    p -> p.getMeeting().getRecall().getBaseUrl(), (p, v) -> p.getMeeting().getRecall().setBaseUrl(v)),
            new Key("whatsapp.api-key", "Engageto WhatsApp API key", true,
                    p -> p.getWhatsapp().getApiKey(), (p, v) -> p.getWhatsapp().setApiKey(v)),
            new Key("whatsapp.base-url", "Engageto WhatsApp base URL", false,
                    p -> p.getWhatsapp().getBaseUrl(), (p, v) -> p.getWhatsapp().setBaseUrl(v)),
            new Key("whatsapp.phone-fields", "Lead fields to read a phone number from (comma separated)", false,
                    p -> p.getWhatsapp().getPhoneFields(), (p, v) -> p.getWhatsapp().setPhoneFields(v)),
            new Key("whatsapp.default-country-code", "Country code to prefix bare local numbers with", false,
                    p -> p.getWhatsapp().getDefaultCountryCode(), (p, v) -> p.getWhatsapp().setDefaultCountryCode(v)),
            new Key("whatsapp.inbound-statuses", "Engageto message status codes that mean the lead sent it (comma separated)", false,
                    p -> p.getWhatsapp().getInboundStatuses(), (p, v) -> p.getWhatsapp().setInboundStatuses(v)),
            new Key("whatsapp.max-messages-per-lead", "Max WhatsApp messages to include per lead", false,
                    p -> String.valueOf(p.getWhatsapp().getMaxMessagesPerLead()),
                    (p, v) -> p.getWhatsapp().setMaxMessagesPerLead(positiveInt("whatsapp.max-messages-per-lead", v))),
            new Key("security.allowed-origins", "Browser origins allowed to call the SDK (comma separated)", false,
                    p -> String.join(",", p.getSecurity().getAllowedOrigins()),
                    (p, v) -> p.getSecurity().setAllowedOrigins(splitOrigins(v))));

    private final AiSdkProperties properties;
    private final SettingsStore store;
    private final Set<String> fromStore = new LinkedHashSet<>();

    public AiSdkSettings(AiSdkProperties properties, SettingsStore store, SecretStore secretStore) {
        this.properties = properties;
        this.store = store;
        applyStored();
        deriveTokenEncryptionKey(secretStore);
    }

    public AiSdkProperties properties() {
        return properties;
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Key key : KEYS) {
            String value = key.reader().apply(properties);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("label", key.label());
            entry.put("secret", key.secret());
            entry.put("set", hasText(value));
            entry.put("source", fromStore.contains(key.name()) ? "ui" : hasText(value) ? "host" : "unset");
            entry.put("value", key.secret() ? mask(value) : value);
            out.put(key.name(), entry);
        }
        return out;
    }

    public synchronized List<String> missing() {
        return KEYS.stream()
                .filter(key -> "llm.api-key".equals(key.name()))
                .filter(key -> !hasText(key.reader().apply(properties)))
                .map(Key::name)
                .toList();
    }

    public synchronized void save(Map<String, String> values) {
        if (values == null) {
            return;
        }
        for (Key key : KEYS) {
            String value = values.get(key.name());
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            key.writer().accept(properties, trimmed);
            store.put(key.name(), trimmed);
            fromStore.add(key.name());
        }
    }

    private void applyStored() {
        Map<String, String> stored = store.all();
        for (Key key : KEYS) {
            String value = stored.get(key.name());
            if (!hasText(value)) {
                continue;
            }
            try {
                key.writer().accept(properties, value.trim());
                fromStore.add(key.name());
            } catch (RuntimeException e) {
                log.warn("ai-sdk: ignoring stored setting {} ({})", key.name(), e.getMessage());
            }
        }
    }

    private void deriveTokenEncryptionKey(SecretStore secretStore) {
        AiSdkProperties.Google google = properties.getMeeting().getGoogle();
        if (hasText(google.getTokenEncryptionKey())) {
            return;
        }
        google.setTokenEncryptionKey(secretStore.getOrCreate(GOOGLE_TOKEN_KEY, AiSdkSettings::randomKey));
        log.debug("ai-sdk: generated a Google token encryption key and stored it in the SDK store");
    }

    private static int positiveInt(String name, String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a whole number, got '" + value + "'");
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return parsed;
    }

    private static List<String> splitOrigins(String value) {
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty())
                .toList();
    }

    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String mask(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 6 ? "••••" : "••••" + trimmed.substring(trimmed.length() - 4);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
