package com.aipack.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

@Component
public class WebhookAuthenticator {

    private final WebhookProperties webhookProperties;

    public WebhookAuthenticator(WebhookProperties webhookProperties) {
        this.webhookProperties = webhookProperties;
    }

    public void requireValidSecret(String providedSecret) {
        String expected = webhookProperties.secret();
        if (expected == null
                || expected.isBlank()
                || providedSecret == null
                || providedSecret.isBlank()
                || !MessageDigest.isEqual(sha256(expected), sha256(providedSecret))) {
            throw new WebhookUnauthorizedException();
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
