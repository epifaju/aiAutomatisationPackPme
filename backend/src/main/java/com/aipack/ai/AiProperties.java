package com.aipack.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, Duration timeout, Ollama ollama, Cache cache) {

    public record Ollama(String baseUrl, String model) {}

    public record Cache(boolean enabled, Duration ttl, String redisHost, int redisPort) {}
}
