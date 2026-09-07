package com.aipack.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayDemoSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayDemoSeedConfig.class);

    @Bean
    FlywayConfigurationCustomizer flywayDemoSeedCustomizer(
            @Value("${app.env:development}") String appEnv,
            @Value("${app.demo-seed-enabled:}") String demoSeedEnabled) {
        return (FluentConfiguration configuration) -> {
            boolean enabled = DemoDataSupport.isEnabled(demoSeedEnabled, appEnv);
            if (enabled) {
                configuration.locations("classpath:db/migration", "classpath:db/seed");
                log.info("Flyway: seed démo activé (APP_ENV={}, DEMO_SEED_ENABLED={})", appEnv, blankToAuto(demoSeedEnabled));
            } else {
                configuration.locations("classpath:db/migration");
                log.info(
                        "Flyway: seed démo désactivé (APP_ENV={}, DEMO_SEED_ENABLED={})",
                        appEnv,
                        blankToAuto(demoSeedEnabled));
            }
        };
    }

    private static String blankToAuto(String value) {
        return value == null || value.isBlank() ? "auto" : value.trim();
    }
}
