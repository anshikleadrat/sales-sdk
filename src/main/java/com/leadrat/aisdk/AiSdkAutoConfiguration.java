package com.leadrat.aisdk;

import tools.jackson.databind.ObjectMapper;
import com.leadrat.aisdk.audit.AuditLogService;
import com.leadrat.aisdk.config.AiSdkProperties;
import com.leadrat.aisdk.config.ReadOnlyEntityManagerProvider;
import com.leadrat.aisdk.config.SqliteStore;
import com.leadrat.aisdk.configure.ConfigRepository;
import com.leadrat.aisdk.configure.ConfigureApiController;
import com.leadrat.aisdk.configure.ConfigureUiController;
import com.leadrat.aisdk.introspection.SchemaIntrospector;
import com.leadrat.aisdk.license.LicenseValidator;
import com.leadrat.aisdk.llm.OpenRouterClient;
import com.leadrat.aisdk.query.QueryCache;
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
    public OpenRouterClient aiSdkOpenRouterClient(AiSdkProperties properties) {
        return new OpenRouterClient(properties);
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
    public QueryCache aiSdkQueryCache(AiSdkProperties properties) {
        return new QueryCache(properties);
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
                                                Summarizer summarizer, QueryCache cache, RateLimiter rateLimiter,
                                                AuditLogService auditLog) {
        return new QueryController(properties, catalog, planner, validator, traversalEngine, summarizer, cache,
                rateLimiter, auditLog);
    }

    @Bean
    public ConfigureUiController aiSdkConfigureUiController() {
        return new ConfigureUiController();
    }

    @Bean
    public ConfigureApiController aiSdkConfigureApiController(ConfigRepository configRepository,
                                                              SchemaIntrospector introspector,
                                                              PasswordStore passwordStore,
                                                              SdkCredentials credentials,
                                                              ReadOnlyEntityManagerProvider readOnlyProvider,
                                                              AuditLogService auditLog,
                                                              AiSdkProperties properties) {
        return new ConfigureApiController(configRepository, introspector, passwordStore, credentials, readOnlyProvider, auditLog, properties);
    }

    @Bean
    public LicenseValidator aiSdkLicenseValidator(AiSdkProperties properties, ObjectMapper objectMapper) {
        return new LicenseValidator(properties, objectMapper);
    }

    @Bean
    public ApplicationRunner aiSdkStartupRunner(SchemaIntrospector introspector, LicenseValidator licenseValidator,
                                                SdkCredentials credentials, PasswordStore passwordStore) {
        return args -> {
            try {
                introspector.scan();
            } catch (RuntimeException e) {
                log.warn("ai-sdk: startup introspection failed ({})", e.toString());
            }
            announceSetup(credentials, passwordStore);
            licenseValidator.start();
        };
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
