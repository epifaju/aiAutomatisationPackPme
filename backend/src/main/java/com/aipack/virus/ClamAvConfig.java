package com.aipack.virus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClamAvProperties.class)
public class ClamAvConfig {

    private static final Logger log = LoggerFactory.getLogger(ClamAvConfig.class);

    @Bean
    VirusScanner virusScanner(ClamAvProperties properties, @Value("${app.env:development}") String appEnv) {
        boolean enabled = ClamAvSupport.isEnabled(properties.enabled(), appEnv);
        boolean failOpen = ClamAvSupport.isFailOpen(properties.failOpen(), appEnv);
        if (!enabled) {
            log.info("ClamAV désactivé (APP_ENV={}, CLAMAV_ENABLED={})", appEnv, blankToAuto(properties.enabled()));
            return new NoOpVirusScanner();
        }
        log.info(
                "ClamAV activé host={}:{} fail-open={} (APP_ENV={}, CLAMAV_FAIL_OPEN={})",
                properties.hostOrDefault(),
                properties.portOrDefault(),
                failOpen,
                appEnv,
                blankToAuto(properties.failOpen()));
        return new ClamAvVirusScanner(properties, failOpen);
    }

    private static String blankToAuto(String value) {
        return value == null || value.isBlank() ? "auto" : value.trim();
    }
}
