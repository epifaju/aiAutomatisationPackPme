package com.aipack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(long maxSizeBytes) {

    public long maxSizeOrDefault() {
        return maxSizeBytes > 0 ? maxSizeBytes : 20L * 1024 * 1024;
    }
}
