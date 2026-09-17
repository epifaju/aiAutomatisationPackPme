package com.aipack.config;

public class WebhookTenantMismatchException extends RuntimeException {

    public WebhookTenantMismatchException() {
        super("Le secret webhook ne correspond pas à cette entreprise");
    }
}
