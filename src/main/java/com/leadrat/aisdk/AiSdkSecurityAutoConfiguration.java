package com.leadrat.aisdk;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration
@ConditionalOnClass({SecurityFilterChain.class, HttpSecurity.class})
@ConditionalOnProperty(prefix = "ai-sdk", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiSdkSecurityAutoConfiguration {

    /**
     * The SDK authenticates /ai-sdk/** with its own JwtAuthFilter, which runs ahead of Spring
     * Security. Without a chain of its own, a host resource server would try to decode the SDK's
     * tokens as its own and reject them, so this chain takes /ai-sdk/** out of the host's chain.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    public SecurityFilterChain aiSdkSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/ai-sdk/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
