package com.aipack.config;

public class WebhookUnauthorizedException extends RuntimeException {

    public WebhookUnauthorizedException() {
        super("Webhook non authentifié");
    }
}
