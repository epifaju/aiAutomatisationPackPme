package com.aipack.config;

import com.aipack.auth.JsonAccessDeniedHandler;
import com.aipack.auth.JsonAuthEntryPoint;
import com.aipack.auth.JwtAuthenticationFilter;
import com.aipack.auth.JwtProperties;
import com.aipack.ratelimit.RateLimitFilter;
import com.aipack.ratelimit.RateLimitProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    @Value("${app.env:development}")
    private String appEnv;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RateLimitFilter rateLimitFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonAuthEntryPoint jsonAuthEntryPoint,
            JsonAccessDeniedHandler jsonAccessDeniedHandler)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonAuthEntryPoint)
                        .accessDeniedHandler(jsonAccessDeniedHandler))
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults())
                            .frameOptions(frame -> frame.deny())
                            .referrerPolicy(referrer ->
                                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                            .contentSecurityPolicy(csp -> csp.policyDirectives(
                                    "default-src 'self'; frame-ancestors 'none'; form-action 'self'"));
                    if (isProduction()) {
                        headers.httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000));
                    }
                })
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/webhook/leads/create",
                                "/webhook/email/incoming",
                                "/webhook/documents/process",
                                "/webhook/invoices/reminder",
                                "/webhook/reports/daily",
                                "/webhook/audit/n8n-error")
                        .permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/settings")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/settings/webhook-secret/rotate")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/automations/*/auto-send")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private boolean isProduction() {
        return "production".equalsIgnoreCase(appEnv) || "prod".equalsIgnoreCase(appEnv);
    }
}
