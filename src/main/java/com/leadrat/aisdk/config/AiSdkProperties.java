package com.leadrat.aisdk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai-sdk")
public class AiSdkProperties {

    private boolean enabled = true;
    private Security security = new Security();
    private Storage storage = new Storage();
    private Llm llm = new Llm();
    private Query query = new Query();
    private License license = new License();

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
}
