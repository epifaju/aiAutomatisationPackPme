package com.aipack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String provider, String endpoint, String accessKey, String secretKey, String bucket) {

    public String providerOrDefault() {
        return provider == null || provider.isBlank() ? "minio" : provider.trim();
    }
}
