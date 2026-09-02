package com.aipack.config;

import com.aipack.auth.JwtAuthenticationFilter;
import com.aipack.auth.JsonAuthEntryPoint;
import com.aipack.auth.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Value("${app.env:development}")
    private String appEnv;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonAuthEntryPoint jsonAuthEntryPoint)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonAuthEntryPoint))
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
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private boolean isProduction() {
        return "production".equalsIgnoreCase(appEnv) || "prod".equalsIgnoreCase(appEnv);
    }
}
