package com.aipack.virus;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClamAvProperties.class)
public class ClamAvConfig {

    @Bean
    VirusScanner virusScanner(ClamAvProperties properties) {
        if (!properties.enabled()) {
            return new NoOpVirusScanner();
        }
        return new ClamAvVirusScanner(properties);
    }
}
