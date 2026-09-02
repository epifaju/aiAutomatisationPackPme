package com.aipack.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.base-url:http://localhost:5173}") String appBaseUrl,
            @Value("${app.cors.allowed-origins:}") String extraOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins(appBaseUrl, extraOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> allowedOrigins(String appBaseUrl, String extraOrigins) {
        List<String> origins = new java.util.ArrayList<>();
        origins.add(appBaseUrl);
        if (extraOrigins != null && !extraOrigins.isBlank()) {
            Arrays.stream(extraOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isEmpty())
                    .forEach(origins::add);
        }
        return origins;
    }
}
