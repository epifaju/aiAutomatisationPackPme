package com.aipack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.invoices")
public record InvoiceProperties(boolean autoSend, boolean schedulerEnabled, String fromAddress) {

    public String fromAddressOrDefault() {
        return fromAddress == null || fromAddress.isBlank() ? "relances@demo.aipack.example" : fromAddress.trim();
    }
}
