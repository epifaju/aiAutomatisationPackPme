package com.aipack.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled, boolean trustForwardedHeaders, Limit auth, Limit webhook) {

    public record Limit(int capacity, Duration refillPeriod) {}
}
