package com.aipack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.webhook")
public record WebhookProperties(String secret) {}
