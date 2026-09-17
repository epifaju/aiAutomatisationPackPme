package com.aipack.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSecretHasherTest {

    @Test
    void sha256HexIsStableAndGenerateIsUnique() {
        assertThat(WebhookSecretHasher.sha256Hex("test-webhook-secret-16")).hasSize(64);
        assertThat(WebhookSecretHasher.sha256Hex("test-webhook-secret-16"))
                .isEqualTo(WebhookSecretHasher.sha256Hex("test-webhook-secret-16"));
        assertThat(WebhookSecretHasher.generatePlaintext())
                .isNotEqualTo(WebhookSecretHasher.generatePlaintext())
                .hasSize(64);
    }
}
