package com.leadrat.aisdk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai-sdk")
public class AiSdkProperties {

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean enabled = true;
    private Security security = new Security();
    private Storage storage = new Storage();
    private Llm llm = new Llm();
    private Query query = new Query();
    private License license = new License();
    private Meeting meeting = new Meeting();
    private Whatsapp whatsapp = new Whatsapp();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }
    public Query getQuery() { return query; }
    public void setQuery(Query query) { this.query = query; }
    public License getLicense() { return license; }
    public void setLicense(License license) { this.license = license; }
    public Meeting getMeeting() { return meeting; }
    public void setMeeting(Meeting meeting) { this.meeting = meeting; }
    public Whatsapp getWhatsapp() { return whatsapp; }
    public void setWhatsapp(Whatsapp whatsapp) { this.whatsapp = whatsapp; }

    public static class Security {
        private String otp;
        private String jwtSecret;
        private int jwtExpiryMinutes = 60;
        private List<String> allowedOrigins = new ArrayList<>();

        public String getOtp() { return otp; }
        public void setOtp(String otp) { this.otp = otp; }
        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        public int getJwtExpiryMinutes() { return jwtExpiryMinutes; }
        public void setJwtExpiryMinutes(int jwtExpiryMinutes) { this.jwtExpiryMinutes = jwtExpiryMinutes; }
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    public static class Storage {
        private String sqlitePath = "./ai-sdk-data/sdk-config.db";

        public String getSqlitePath() { return sqlitePath; }
        public void setSqlitePath(String sqlitePath) { this.sqlitePath = sqlitePath; }
    }

    public static class Llm {
        public static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";
        public static final String DEFAULT_MODEL = "anthropic/claude-sonnet-4.5";

        private String provider = env("OPENROUTER_PROVIDER", "openrouter");
        private String baseUrl = env("OPENROUTER_BASE_URL", DEFAULT_BASE_URL);
        private String apiKey = env("OPENROUTER_API_KEY", null);
        private String plannerModel = env("OPENROUTER_PLANNER_MODEL", env("OPENROUTER_MODEL", DEFAULT_MODEL));
        private String summarizerModel = env("OPENROUTER_SUMMARIZER_MODEL", env("OPENROUTER_MODEL", DEFAULT_MODEL));
        private int timeoutSeconds = 30;

        private static String env(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        private static String orDefault(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = orDefault(provider, "openrouter"); }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = orDefault(baseUrl, DEFAULT_BASE_URL); }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = orDefault(apiKey, this.apiKey); }
        public String getPlannerModel() { return plannerModel; }
        public void setPlannerModel(String plannerModel) { this.plannerModel = orDefault(plannerModel, DEFAULT_MODEL); }
        public String getSummarizerModel() { return summarizerModel; }
        public void setSummarizerModel(String summarizerModel) { this.summarizerModel = orDefault(summarizerModel, DEFAULT_MODEL); }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Query {
        private int cacheTtlMinutes = 20;
        private int maxTargetsPerRequest = 25;
        private int defaultChildDepth = 1;
        private int defaultParentDepth = 2;
        private int maxChildDepth = 3;
        private int maxParentDepth = 3;
        private int maxChildrenPerRelation = 50;
        private int dbTimeoutSeconds = 5;
        private int rateLimitPerMinute = 30;

        public int getCacheTtlMinutes() { return cacheTtlMinutes; }
        public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
        public int getMaxTargetsPerRequest() { return maxTargetsPerRequest; }
        public void setMaxTargetsPerRequest(int maxTargetsPerRequest) { this.maxTargetsPerRequest = maxTargetsPerRequest; }
        public int getDefaultChildDepth() { return defaultChildDepth; }
        public void setDefaultChildDepth(int defaultChildDepth) { this.defaultChildDepth = defaultChildDepth; }
        public int getDefaultParentDepth() { return defaultParentDepth; }
        public void setDefaultParentDepth(int defaultParentDepth) { this.defaultParentDepth = defaultParentDepth; }
        public int getMaxChildDepth() { return maxChildDepth; }
        public void setMaxChildDepth(int maxChildDepth) { this.maxChildDepth = maxChildDepth; }
        public int getMaxParentDepth() { return maxParentDepth; }
        public void setMaxParentDepth(int maxParentDepth) { this.maxParentDepth = maxParentDepth; }
        public int getMaxChildrenPerRelation() { return maxChildrenPerRelation; }
        public void setMaxChildrenPerRelation(int maxChildrenPerRelation) { this.maxChildrenPerRelation = maxChildrenPerRelation; }
        public int getDbTimeoutSeconds() { return dbTimeoutSeconds; }
        public void setDbTimeoutSeconds(int dbTimeoutSeconds) { this.dbTimeoutSeconds = dbTimeoutSeconds; }
        public int getRateLimitPerMinute() { return rateLimitPerMinute; }
        public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
    }

    public static class License {
        private String key;
        private String serverUrl;
        private int checkIntervalHours = 24;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
        public int getCheckIntervalHours() { return checkIntervalHours; }
        public void setCheckIntervalHours(int checkIntervalHours) { this.checkIntervalHours = checkIntervalHours; }
    }

    public static class Meeting {
        private Boolean enabled;
        private String leadEntity = "Lead";
        private int maxDiscussionsPerLead = 5;
        private int discussionCharLimit = 6000;
        private Google google = new Google();
        private Recall recall = new Recall();

        public boolean isActive() { return enabled != null ? enabled : google.isActive() || recall.isActive(); }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getLeadEntity() { return leadEntity; }
        public void setLeadEntity(String leadEntity) { this.leadEntity = leadEntity; }
        public int getMaxDiscussionsPerLead() { return maxDiscussionsPerLead; }
        public void setMaxDiscussionsPerLead(int maxDiscussionsPerLead) { this.maxDiscussionsPerLead = maxDiscussionsPerLead; }
        public int getDiscussionCharLimit() { return discussionCharLimit; }
        public void setDiscussionCharLimit(int discussionCharLimit) { this.discussionCharLimit = discussionCharLimit; }
        public Google getGoogle() { return google; }
        public void setGoogle(Google google) { this.google = google; }
        public Recall getRecall() { return recall; }
        public void setRecall(Recall recall) { this.recall = recall; }
    }

    public static class Google {
        private Boolean enabled;
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";
        private String tokenEncryptionKey = "";
        private String postConnectRedirect = "";

        public boolean isActive() { return enabled != null ? enabled : hasText(clientId) && hasText(clientSecret); }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
        public String getTokenEncryptionKey() { return tokenEncryptionKey; }
        public void setTokenEncryptionKey(String tokenEncryptionKey) { this.tokenEncryptionKey = tokenEncryptionKey; }
        public String getPostConnectRedirect() { return postConnectRedirect; }
        public void setPostConnectRedirect(String postConnectRedirect) { this.postConnectRedirect = postConnectRedirect; }
    }

    public static class Recall {
        private Boolean enabled;
        private String baseUrl = "https://us-east-1.recall.ai";
        private String apiKey = "";
        private String webhookSecret = "";
        private String botName = "AI SDK Notetaker";
        private boolean storeTranscript = true;
        private String language = "auto";
        private boolean autoTranscribe = true;
        private String transcriptMode = "prioritize_accuracy";
        private int joinEarlyMinutes = 2;
        private int joinGraceMinutes = 30;
        private int directBotWindowMinutes = 15;
        private int reconcileSeconds = 600;

        public boolean isActive() { return enabled != null ? enabled : hasText(apiKey); }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getWebhookSecret() { return webhookSecret; }
        public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
        public String getBotName() { return botName; }
        public void setBotName(String botName) { this.botName = botName; }
        public boolean isStoreTranscript() { return storeTranscript; }
        public void setStoreTranscript(boolean storeTranscript) { this.storeTranscript = storeTranscript; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public boolean isAutoTranscribe() { return autoTranscribe; }
        public void setAutoTranscribe(boolean autoTranscribe) { this.autoTranscribe = autoTranscribe; }
        public String getTranscriptMode() { return transcriptMode; }
        public void setTranscriptMode(String transcriptMode) { this.transcriptMode = transcriptMode; }
        public int getJoinEarlyMinutes() { return joinEarlyMinutes; }
        public void setJoinEarlyMinutes(int joinEarlyMinutes) { this.joinEarlyMinutes = joinEarlyMinutes; }
        public int getJoinGraceMinutes() { return joinGraceMinutes; }
        public void setJoinGraceMinutes(int joinGraceMinutes) { this.joinGraceMinutes = joinGraceMinutes; }
        public int getDirectBotWindowMinutes() { return directBotWindowMinutes; }
        public void setDirectBotWindowMinutes(int directBotWindowMinutes) { this.directBotWindowMinutes = directBotWindowMinutes; }
        public int getReconcileSeconds() { return reconcileSeconds; }
        public void setReconcileSeconds(int reconcileSeconds) { this.reconcileSeconds = reconcileSeconds; }
    }

    public static class Whatsapp {
        private Boolean enabled;
        private String baseUrl = "https://connect.engageto.in";
        private String apiKey = "";
        private int pageSize = 50;
        private int maxPages = 2;
        private int maxMessagesPerLead = 60;
        private int chatCharLimit = 6000;
        private int cacheTtlMinutes = 10;
        private String inboundStatuses = "3,6";
        private String phoneFields = "mobile,mobileNumber,phone,phoneNumber,contactNo,contactNumber";
        private String defaultCountryCode = "91";

        public boolean isActive() { return enabled != null ? enabled : hasText(apiKey); }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public int getMaxPages() { return maxPages; }
        public void setMaxPages(int maxPages) { this.maxPages = maxPages; }
        public int getMaxMessagesPerLead() { return maxMessagesPerLead; }
        public void setMaxMessagesPerLead(int maxMessagesPerLead) { this.maxMessagesPerLead = maxMessagesPerLead; }
        public int getChatCharLimit() { return chatCharLimit; }
        public void setChatCharLimit(int chatCharLimit) { this.chatCharLimit = chatCharLimit; }
        public int getCacheTtlMinutes() { return cacheTtlMinutes; }
        public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
        public String getInboundStatuses() { return inboundStatuses; }
        public void setInboundStatuses(String inboundStatuses) { this.inboundStatuses = inboundStatuses; }
        public String getPhoneFields() { return phoneFields; }
        public void setPhoneFields(String phoneFields) { this.phoneFields = phoneFields; }
        public String getDefaultCountryCode() { return defaultCountryCode; }
        public void setDefaultCountryCode(String defaultCountryCode) { this.defaultCountryCode = defaultCountryCode; }
    }
}
