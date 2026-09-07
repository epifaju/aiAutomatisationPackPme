package com.aipack.config;

import com.aipack.auth.JwtProperties;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Refuse de démarrer en production si des secrets placeholder / trop faibles sont détectés (P0.1).
 */
@Component
@EnableConfigurationProperties({JwtProperties.class, WebhookProperties.class, StorageProperties.class})
public class ProductionSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretsValidator.class);

    private static final Set<String> FORBIDDEN_EXACT = Set.of(
            "change-me-jwt-secret-min-32-characters-long",
            "change-me-webhook-secret-min-16-chars",
            "aipack-dev-change-me",
            "minioadmin-change-me",
            "minioadmin",
            "change-me-n8n-encryption-key-32b");

    private final String appEnv;
    private final JwtProperties jwtProperties;
    private final WebhookProperties webhookProperties;
    private final StorageProperties storageProperties;
    private final String datasourcePassword;

    public ProductionSecretsValidator(
            @Value("${app.env:development}") String appEnv,
            JwtProperties jwtProperties,
            WebhookProperties webhookProperties,
            StorageProperties storageProperties,
            @Value("${spring.datasource.password:}") String datasourcePassword) {
        this.appEnv = appEnv;
        this.jwtProperties = jwtProperties;
        this.webhookProperties = webhookProperties;
        this.storageProperties = storageProperties;
        this.datasourcePassword = datasourcePassword;
    }

    @PostConstruct
    public void validate() {
        if (!isProduction(appEnv)) {
            return;
        }
        List<String> errors = new ArrayList<>();
        checkSecret("JWT_SECRET (app.jwt.secret)", jwtProperties.secret(), 32, errors);
        checkSecret("WEBHOOK_SECRET (app.webhook.secret)", webhookProperties.secret(), 16, errors);
        checkSecret("POSTGRES_PASSWORD / spring.datasource.password", datasourcePassword, 12, errors);
        if (!"memory".equalsIgnoreCase(storageProperties.providerOrDefault())) {
            checkSecret("MINIO_ACCESS_KEY (app.storage.access-key)", storageProperties.accessKey(), 3, errors);
            checkSecret("MINIO_SECRET_KEY (app.storage.secret-key)", storageProperties.secretKey(), 8, errors);
        }
        if (!errors.isEmpty()) {
            String message =
                    "Refus de démarrer : APP_ENV="
                            + appEnv
                            + " avec secrets de développement ou trop faibles. "
                            + String.join(" ; ", errors)
                            + ". Voir docs/security.md (P0.1).";
            log.error(message);
            throw new IllegalStateException(message);
        }
        log.info("Contrôle secrets production OK (APP_ENV={})", appEnv);
    }

    static boolean isProduction(String appEnv) {
        if (appEnv == null || appEnv.isBlank()) {
            return false;
        }
        String normalized = appEnv.trim().toLowerCase(Locale.ROOT);
        return "production".equals(normalized) || "prod".equals(normalized);
    }

    static void checkSecret(String name, String value, int minLength, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(name + " est vide");
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() < minLength) {
            errors.add(name + " trop court (min " + minLength + " caractères)");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_EXACT.contains(lower) || FORBIDDEN_EXACT.contains(trimmed)) {
            errors.add(name + " utilise encore une valeur placeholder (.env.example)");
            return;
        }
        if (lower.contains("change-me") || lower.contains("changeme")) {
            errors.add(name + " contient un marqueur placeholder (change-me)");
        }
    }
}
