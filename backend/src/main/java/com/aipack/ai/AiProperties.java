package com.aipack.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, Duration timeout, Ollama ollama, OpenAi openai, Cache cache) {

    public record Ollama(String baseUrl, String model) {}

    /**
     * API Chat Completions compatible OpenAI ({@code /v1/chat/completions}).
     */
    public record OpenAi(String baseUrl, String apiKey, String model, Boolean jsonMode) {}

    public record Cache(boolean enabled, Duration ttl, String redisHost, int redisPort) {}

    public String normalizedProvider() {
        if (provider == null || provider.isBlank()) {
            return "ollama";
        }
        String value = provider.trim().toLowerCase();
        if ("openai-compatible".equals(value) || "external".equals(value)) {
            return "openai";
        }
        return value;
    }
}
