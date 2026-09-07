package com.aipack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aipack.auth.JwtProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionSecretsValidatorTest {

    @Test
    void isProductionRecognizesProdAliases() {
        assertThat(ProductionSecretsValidator.isProduction("production")).isTrue();
        assertThat(ProductionSecretsValidator.isProduction("PROD")).isTrue();
        assertThat(ProductionSecretsValidator.isProduction("development")).isFalse();
        assertThat(ProductionSecretsValidator.isProduction(null)).isFalse();
    }

    @Test
    void checkSecretRejectsPlaceholdersAndShortValues() {
        List<String> errors = new ArrayList<>();
        ProductionSecretsValidator.checkSecret(
                "JWT_SECRET", "change-me-jwt-secret-min-32-characters-long", 32, errors);
        ProductionSecretsValidator.checkSecret("WEBHOOK_SECRET", "short", 16, errors);
        ProductionSecretsValidator.checkSecret("MINIO_SECRET_KEY", "minioadmin-change-me", 8, errors);
        assertThat(errors).hasSize(3);
    }

    @Test
    void checkSecretAcceptsStrongValue() {
        List<String> errors = new ArrayList<>();
        ProductionSecretsValidator.checkSecret(
                "JWT_SECRET", "prod-jwt-secret-with-enough-entropy-xyz-2026", 32, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void productionBootFailsOnPlaceholderJwt() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                "production",
                new JwtProperties("change-me-jwt-secret-min-32-characters-long", Duration.ofMinutes(15), Duration.ofDays(7)),
                new WebhookProperties("a-strong-webhook-secret-here"),
                new StorageProperties("memory", "http://localhost:9000", "test", "test-secret", "bucket"),
                "strong-db-password-ok");
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("placeholder");
    }

    @Test
    void developmentBootAllowsPlaceholders() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                "development",
                new JwtProperties("change-me-jwt-secret-min-32-characters-long", Duration.ofMinutes(15), Duration.ofDays(7)),
                new WebhookProperties("change-me-webhook-secret-min-16-chars"),
                new StorageProperties("minio", "http://localhost:9000", "minioadmin", "minioadmin-change-me", "aipack"),
                "aipack-dev-change-me");
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void productionBootSucceedsWithStrongSecrets() {
        ProductionSecretsValidator validator = new ProductionSecretsValidator(
                "production",
                new JwtProperties("prod-jwt-secret-with-enough-entropy-xyz-2026", Duration.ofMinutes(15), Duration.ofDays(7)),
                new WebhookProperties("prod-webhook-secret-long-enough"),
                new StorageProperties("minio", "http://minio:9000", "aipackprod", "minio-prod-secret-key-9f3a", "aipack"),
                "postgres-prod-password-9f3a");
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
