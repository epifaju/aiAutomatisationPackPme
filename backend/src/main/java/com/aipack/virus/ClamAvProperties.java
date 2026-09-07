package com.aipack.virus;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.clamav")
public record ClamAvProperties(boolean enabled, String host, int port, Duration timeout, boolean failOpen) {

    public String hostOrDefault() {
        return host == null || host.isBlank() ? "localhost" : host.trim();
    }

    public int portOrDefault() {
        return port > 0 ? port : 3310;
    }

    public Duration timeoutOrDefault() {
        return timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(10) : timeout;
    }
}
