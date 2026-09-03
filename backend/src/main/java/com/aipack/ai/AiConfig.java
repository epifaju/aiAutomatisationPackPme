package com.aipack.ai;

import com.aipack.config.WebhookProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AiProperties.class, WebhookProperties.class})
public class AiConfig {

    @Bean
    AiResponseCache aiResponseCache(AiProperties properties) {
        AiProperties.Cache cache = properties.cache();
        if (cache == null || !cache.enabled()) {
            return new NoOpAiResponseCache();
        }
        return new RedisAiResponseCache(cache.redisHost(), cache.redisPort(), cache.ttl());
    }
}
