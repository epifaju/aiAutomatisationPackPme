package com.aipack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reports")
public record ReportProperties(boolean autoSend, boolean schedulerEnabled, String fromAddress) {

    public String fromAddressOrDefault() {
        return fromAddress == null || fromAddress.isBlank() ? "rapports@demo.aipack.example" : fromAddress.trim();
    }
}
