package com.leadrat.aisdk;

import tools.jackson.databind.ObjectMapper;
import com.leadrat.aisdk.audit.AuditLogService;
import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.config.ReadOnlyEntityManagerProvider;
import com.leadrat.aisdk.config.AiSdkSettings;
import com.leadrat.aisdk.config.SettingsStore;
import com.leadrat.aisdk.config.SqliteStore;
import com.leadrat.aisdk.configure.ConfigRepository;
import com.leadrat.aisdk.configure.ConfigureApiController;
import com.leadrat.aisdk.configure.ConfigureUiController;
import com.leadrat.aisdk.configure.SettingsController;
import com.leadrat.aisdk.introspection.SchemaIntrospector;
import com.leadrat.aisdk.license.LicenseValidator;
import com.leadrat.aisdk.llm.OpenRouterClient;
import com.leadrat.aisdk.meeting.GoogleCalendarClient;
import com.leadrat.aisdk.meeting.GoogleCredentialStore;
import com.leadrat.aisdk.meeting.GoogleOAuthController;
import com.leadrat.aisdk.meeting.GoogleTokenStore;
import com.leadrat.aisdk.meeting.MeetingController;
import com.leadrat.aisdk.meeting.MeetingDiscussionProvider;
import com.leadrat.aisdk.meeting.MeetingReconciler;
import com.leadrat.aisdk.meeting.MeetingService;
import com.leadrat.aisdk.meeting.MeetingStore;
import com.leadrat.aisdk.meeting.RecallApiClient;
import com.leadrat.aisdk.meeting.RecallBotConfigFactory;
import com.leadrat.aisdk.meeting.RecallCalendarClient;
import com.leadrat.aisdk.meeting.RecallCalendarService;
import com.leadrat.aisdk.meeting.RecallHttp;
import com.leadrat.aisdk.meeting.RecallPayloadMapper;
import com.leadrat.aisdk.meeting.RecallService;
import com.leadrat.aisdk.meeting.RecallWebhookController;
import com.leadrat.aisdk.query.QueryController;
import com.leadrat.aisdk.query.QueryPlanValidator;
import com.leadrat.aisdk.query.QueryPlanner;
import com.leadrat.aisdk.query.RateLimiter;
import com.leadrat.aisdk.query.ResultSerializer;
import com.leadrat.aisdk.query.SchemaCatalog;
import com.leadrat.aisdk.query.SpecificationBuilder;
import com.leadrat.aisdk.query.Summarizer;
import com.leadrat.aisdk.query.TraversalEngine;
import com.leadrat.aisdk.security.AiSdkCorsFilter;
import com.leadrat.aisdk.security.AuthController;
import com.leadrat.aisdk.security.JwtAuthFilter;
import com.leadrat.aisdk.security.JwtService;
import com.leadrat.aisdk.security.PasswordStore;
import com.leadrat.aisdk.security.SdkCredentials;
import com.leadrat.aisdk.security.SecretStore;
import com.leadrat.aisdk.security.SetupController;
import com.leadrat.aisdk.whatsapp.EngagetoClient;
import com.leadrat.aisdk.whatsapp.EngagetoHttp;
import com.leadrat.aisdk.whatsapp.LeadPhoneResolver;
import com.leadrat.aisdk.whatsapp.WhatsappChatService;
import com.leadrat.aisdk.whatsapp.WhatsappContextProvider;
import com.leadrat.aisdk.whatsapp.WhatsappController;
import com.leadrat.aisdk.whatsapp.WhatsappPayloadMapper;
import com.leadrat.aisdk.whatsapp.WhatsappStore;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
@EnableConfigurationProperties(AiSdkProperties.class)
@ConditionalOnProperty(prefix = "ai-sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiSdkAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiSdkAutoConfiguration.class);

    @Bean
    public SqliteStore aiSdkSqliteStore(AiSdkProperties properties) {
        return new SqliteStore(properties);
    }

    @Bean
    public ConfigRepository aiSdkConfigRepository(SqliteStore store) {
        return new ConfigRepository(store.jdbc());
    }

    @Bean
    public PasswordStore aiSdkPasswordStore(SqliteStore store) {
        return new PasswordStore(store.jdbc());
    }

    @Bean
    public SecretStore aiSdkSecretStore(SqliteStore store) {
        return new SecretStore(store.jdbc());
    }

    @Bean
    public SettingsStore aiSdkSettingsStore(SqliteStore store) {
        return new SettingsStore(store.jdbc());
    }

    @Bean
    public AiSdkSettings aiSdkSettings(AiSdkProperties properties, SettingsStore settingsStore, SecretStore secretStore) {
        return new AiSdkSettings(properties, settingsStore, secretStore);
    }

    @Bean
    public SdkCredentials aiSdkCredentials(AiSdkProperties properties, SecretStore secretStore) {
        return new SdkCredentials(properties, secretStore);
    }

    @Bean
    public JwtService aiSdkJwtService(AiSdkProperties properties, SdkCredentials credentials) {
        return new JwtService(properties, credentials);
    }

    @Bean
    public FilterRegistrationBean<AiSdkCorsFilter> aiSdkCorsFilter(AiSdkProperties properties) {
        FilterRegistrationBean<AiSdkCorsFilter> registration = new FilterRegistrationBean<>(new AiSdkCorsFilter(properties));
        registration.addUrlPatterns("/ai-sdk/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> aiSdkJwtFilter(JwtService jwtService) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(new JwtAuthFilter(jwtService));
        registration.addUrlPatterns("/ai-sdk/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public SetupController aiSdkSetupController(SdkCredentials credentials, PasswordStore passwordStore) {
        return new SetupController(credentials, passwordStore);
    }

    @Bean
    public AuthController aiSdkAuthController(PasswordStore passwordStore, JwtService jwtService) {
        return new AuthController(passwordStore, jwtService);
    }

    @Bean
    public SchemaIntrospector aiSdkSchemaIntrospector(EntityManagerFactory entityManagerFactory,
                                                      ConfigRepository configRepository) {
        return new SchemaIntrospector(entityManagerFactory, configRepository);
    }

    @Bean
    public ReadOnlyEntityManagerProvider aiSdkReadOnlyEntityManagerProvider(AiSdkProperties properties,
                                                                           EntityManagerFactory entityManagerFactory,
                                                                           DataSource dataSource) {
        return new ReadOnlyEntityManagerProvider(properties, entityManagerFactory, dataSource);
    }

    @Bean
    public SchemaCatalog aiSdkSchemaCatalog(ConfigRepository configRepository, SchemaIntrospector introspector) {
        return new SchemaCatalog(configRepository, introspector);
    }

    @Bean
    public OpenRouterClient aiSdkOpenRouterClient(AiSdkSettings settings) {
        return new OpenRouterClient(settings.properties());
    }

    @Bean
    public QueryPlanner aiSdkQueryPlanner(OpenRouterClient client, AiSdkProperties properties, ObjectMapper objectMapper) {
        return new QueryPlanner(client, properties, objectMapper);
    }

    @Bean
    public QueryPlanValidator aiSdkQueryPlanValidator(AiSdkProperties properties, SchemaCatalog catalog) {
        return new QueryPlanValidator(properties, catalog);
    }

    @Bean
    public ResultSerializer aiSdkResultSerializer(SchemaCatalog catalog) {
        return new ResultSerializer(catalog);
    }

    @Bean
    public SpecificationBuilder aiSdkSpecificationBuilder() {
        return new SpecificationBuilder();
    }

    @Bean
    public TraversalEngine aiSdkTraversalEngine(ReadOnlyEntityManagerProvider provider, SchemaCatalog catalog,
                                                ResultSerializer serializer, SpecificationBuilder specificationBuilder,
                                                AiSdkProperties properties) {
        return new TraversalEngine(provider, catalog, serializer, specificationBuilder, properties);
    }

    @Bean
    public Summarizer aiSdkSummarizer(OpenRouterClient client, AiSdkProperties properties, ObjectMapper objectMapper) {
        return new Summarizer(client, properties, objectMapper);
    }

    @Bean
    public RateLimiter aiSdkRateLimiter(AiSdkProperties properties) {
        return new RateLimiter(properties);
    }

    @Bean
    public AuditLogService aiSdkAuditLogService(SqliteStore store) {
        return new AuditLogService(store.jdbc());
    }

    @Bean
    public QueryController aiSdkQueryController(AiSdkProperties properties, SchemaCatalog catalog, QueryPlanner planner,
                                                QueryPlanValidator validator, TraversalEngine traversalEngine,
                                                Summarizer summarizer, RateLimiter rateLimiter,
                                                AuditLogService auditLog, MeetingDiscussionProvider discussionProvider,
                                                WhatsappContextProvider whatsappProvider) {
        return new QueryController(properties, catalog, planner, validator, traversalEngine, summarizer,
                rateLimiter, auditLog, discussionProvider, whatsappProvider);
    }

    @Bean
    public MeetingStore aiSdkMeetingStore(SqliteStore store, ObjectMapper objectMapper) {
        return new MeetingStore(store.jdbc(), objectMapper);
    }

    @Bean
    public GoogleCredentialStore aiSdkGoogleCredentialStore(SqliteStore store) {
        return new GoogleCredentialStore(store.jdbc());
    }

    @Bean
    public GoogleTokenStore aiSdkGoogleTokenStore(AiSdkProperties properties, GoogleCredentialStore credentialStore) {
        return new GoogleTokenStore(properties, credentialStore);
    }

    @Bean
    public GoogleCalendarClient aiSdkGoogleCalendarClient(AiSdkProperties properties, GoogleTokenStore tokenStore) {
        return new GoogleCalendarClient(properties, tokenStore);
    }

    @Bean
    public RecallHttp aiSdkRecallHttp(AiSdkProperties properties, ObjectMapper objectMapper) {
        return new RecallHttp(properties, objectMapper);
    }

    @Bean
    public RecallApiClient aiSdkRecallApiClient(RecallHttp http, AiSdkProperties properties) {
        return new RecallApiClient(http, properties);
    }

    @Bean
    public RecallCalendarClient aiSdkRecallCalendarClient(RecallHttp http) {
        return new RecallCalendarClient(http);
    }

    @Bean
    public RecallBotConfigFactory aiSdkRecallBotConfigFactory(AiSdkProperties properties) {
        return new RecallBotConfigFactory(properties);
    }

    @Bean
    public RecallPayloadMapper aiSdkRecallPayloadMapper() {
        return new RecallPayloadMapper();
    }

    @Bean
    public RecallCalendarService aiSdkRecallCalendarService(RecallHttp http, RecallCalendarClient calendarClient,
                                                           RecallApiClient apiClient, RecallBotConfigFactory botConfigFactory,
                                                           AiSdkProperties properties, GoogleCredentialStore credentialStore,
                                                           GoogleTokenStore tokenStore, MeetingStore meetingStore) {
        return new RecallCalendarService(http, calendarClient, apiClient, botConfigFactory, properties,
                credentialStore, tokenStore, meetingStore);
    }

    @Bean
    public RecallService aiSdkRecallService(MeetingStore meetingStore, RecallApiClient apiClient,
                                            RecallCalendarService calendarService, RecallPayloadMapper mapper,
                                            AiSdkProperties properties) {
        return new RecallService(meetingStore, apiClient, calendarService, mapper, properties);
    }

    @Bean
    public MeetingService aiSdkMeetingService(AiSdkProperties properties, MeetingStore meetingStore,
                                              GoogleCalendarClient calendarClient, RecallService recallService) {
        return new MeetingService(properties, meetingStore, calendarClient, recallService);
    }

    @Bean
    public MeetingDiscussionProvider aiSdkMeetingDiscussionProvider(AiSdkProperties properties, MeetingStore meetingStore) {
        return new MeetingDiscussionProvider(properties, meetingStore);
    }

    @Bean
    public EngagetoHttp aiSdkEngagetoHttp(AiSdkProperties properties, ObjectMapper objectMapper) {
        return new EngagetoHttp(properties, objectMapper);
    }

    @Bean
    public EngagetoClient aiSdkEngagetoClient(EngagetoHttp http, AiSdkProperties properties) {
        return new EngagetoClient(http, properties);
    }

    @Bean
    public WhatsappPayloadMapper aiSdkWhatsappPayloadMapper() {
        return new WhatsappPayloadMapper();
    }

    @Bean
    public WhatsappStore aiSdkWhatsappStore(SqliteStore store) {
        return new WhatsappStore(store.jdbc());
    }

    @Bean
    public WhatsappChatService aiSdkWhatsappChatService(EngagetoClient client, EngagetoHttp http,
                                                        WhatsappPayloadMapper mapper, WhatsappStore store,
                                                        AiSdkProperties properties) {
        return new WhatsappChatService(client, http, mapper, store, properties);
    }

    @Bean
    public LeadPhoneResolver aiSdkLeadPhoneResolver(AiSdkProperties properties) {
        return new LeadPhoneResolver(properties);
    }

    @Bean
    public WhatsappContextProvider aiSdkWhatsappContextProvider(AiSdkProperties properties,
                                                                WhatsappChatService chatService,
                                                                LeadPhoneResolver phoneResolver) {
        return new WhatsappContextProvider(properties, chatService, phoneResolver);
    }

    @Bean
    public WhatsappController aiSdkWhatsappController(AiSdkProperties properties, EngagetoHttp http,
                                                       EngagetoClient client, WhatsappChatService chatService,
                                                       LeadPhoneResolver phoneResolver) {
        return new WhatsappController(properties, http, client, chatService, phoneResolver);
    }

    @Bean
    public MeetingController aiSdkMeetingController(AiSdkProperties properties, MeetingService meetingService,
                                                    GoogleCalendarClient calendarClient, RecallHttp recallHttp,
                                                    GoogleCredentialStore credentialStore) {
        return new MeetingController(properties, meetingService, calendarClient, recallHttp, credentialStore);
    }

    @Bean
    public GoogleOAuthController aiSdkGoogleOAuthController(AiSdkProperties properties, GoogleTokenStore tokenStore,
                                                            GoogleCredentialStore credentialStore,
                                                            RecallCalendarService recallCalendarService) {
        return new GoogleOAuthController(properties, tokenStore, credentialStore, recallCalendarService);
    }

    @Bean
    public RecallWebhookController aiSdkRecallWebhookController(AiSdkProperties properties, RecallService recallService,
                                                                ObjectMapper objectMapper) {
        return new RecallWebhookController(properties, recallService, objectMapper);
    }

    @Bean(destroyMethod = "close")
    public MeetingReconciler aiSdkMeetingReconciler(AiSdkProperties properties, RecallHttp http,
                                                    RecallCalendarService calendarService,
                                                    GoogleCredentialStore credentialStore, MeetingStore meetingStore) {
        return new MeetingReconciler(properties, http, calendarService, credentialStore, meetingStore);
    }

    @Bean
    public ConfigureUiController aiSdkConfigureUiController(PasswordStore passwordStore) {
        return new ConfigureUiController(passwordStore);
    }

    @Bean
    public ConfigureApiController aiSdkConfigureApiController(ConfigRepository configRepository,
                                                              SchemaIntrospector introspector,
                                                              PasswordStore passwordStore,
                                                              SdkCredentials credentials,
                                                              ReadOnlyEntityManagerProvider readOnlyProvider,
                                                              AuditLogService auditLog,
                                                              AiSdkSettings settings) {
        return new ConfigureApiController(configRepository, introspector, passwordStore, credentials, readOnlyProvider, auditLog, settings);
    }

    @Bean
    public SettingsController aiSdkSettingsController(AiSdkSettings settings, MeetingReconciler reconciler,
                                                     OpenRouterClient llmClient) {
        return new SettingsController(settings, reconciler, llmClient);
    }

    @Bean
    public LicenseValidator aiSdkLicenseValidator(AiSdkProperties properties, ObjectMapper objectMapper) {
        return new LicenseValidator(properties, objectMapper);
    }

    @Bean
    public ApplicationRunner aiSdkStartupRunner(AiSdkProperties properties, SchemaIntrospector introspector,
                                                LicenseValidator licenseValidator,
                                                SdkCredentials credentials, PasswordStore passwordStore,
                                                MeetingReconciler meetingReconciler, AiSdkSettings settings) {
        return args -> {
            try {
                introspector.scan();
            } catch (RuntimeException e) {
                log.warn("ai-sdk: startup introspection failed ({})", e.toString());
            }
            syncConfiguredPassword(properties, passwordStore);
            announceSetup(credentials, passwordStore);
            announceMissing(settings);
            meetingReconciler.start();
            licenseValidator.start();
        };
    }

    private void announceMissing(AiSdkSettings settings) {
        if (!settings.missing().isEmpty()) {
            log.warn("ai-sdk: no LLM API key yet — open /ai-sdk/settings and paste an OpenRouter key, "
                    + "or set OPENROUTER_API_KEY in the host application. Everything else is already configured.");
        }
    }

    private void syncConfiguredPassword(AiSdkProperties properties, PasswordStore passwordStore) {
        String configured = properties.getSecurity().getAdminPassword();
        if (configured == null || configured.isBlank()) {
            return;
        }
        if (configured.length() < 12) {
            log.warn("ai-sdk: ai-sdk.security.admin-password must be at least 12 characters — ignoring it");
            return;
        }
        passwordStore.ensurePassword(configured);
        log.warn("ai-sdk: admin password from ai-sdk.security.admin-password: {} — open /ai-sdk/auth to sign in.",
                configured);
    }

    private void announceSetup(SdkCredentials credentials, PasswordStore passwordStore) {
        if (passwordStore.isSetupCompleted()) {
            return;
        }
        if (credentials.setupOtpGenerated()) {
            log.warn("ai-sdk: no ai-sdk.security.otp configured, so one was generated and stored: {} — "
                    + "open /ai-sdk/setup to exchange it for an admin password. Set ai-sdk.security.otp "
                    + "explicitly to keep it out of the logs.", credentials.setupOtp());
        } else {
            log.info("ai-sdk: setup is not complete; open /ai-sdk/setup and use the configured OTP");
        }
    }
}
