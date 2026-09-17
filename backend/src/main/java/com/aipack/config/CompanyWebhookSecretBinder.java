package com.aipack.config;

import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * P1.1 — associe {@code WEBHOOK_SECRET} (env) à l’entreprise démo si elle n’a pas encore de hash.
 * Les autres entreprises reçoivent un secret via rotation Settings, pas via le secret global.
 */
@Component
@Order(40)
public class CompanyWebhookSecretBinder implements ApplicationRunner {

    static final UUID DEMO_COMPANY_ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    private static final Logger log = LoggerFactory.getLogger(CompanyWebhookSecretBinder.class);

    private final CompanyRepository companyRepository;
    private final WebhookProperties webhookProperties;

    public CompanyWebhookSecretBinder(CompanyRepository companyRepository, WebhookProperties webhookProperties) {
        this.companyRepository = companyRepository;
        this.webhookProperties = webhookProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String secret = webhookProperties.secret();
        if (secret == null || secret.isBlank()) {
            log.warn("WEBHOOK_SECRET empty — no company webhook hash bound");
            return;
        }
        String envHash = WebhookSecretHasher.sha256Hex(secret);
        if (companyRepository.findByWebhookSecretHash(envHash).isPresent()) {
            return;
        }
        Company target = companyRepository
                .findById(DEMO_COMPANY_ID)
                .filter(company -> company.getWebhookSecretHash() == null)
                .orElseGet(() -> companyRepository.findFirstByWebhookSecretHashIsNull().orElse(null));
        if (target == null) {
            log.debug("No company without webhook hash to bind WEBHOOK_SECRET");
            return;
        }
        target.setWebhookSecretHash(envHash);
        companyRepository.save(target);
        log.info("Bound WEBHOOK_SECRET hash to company {}", target.getId());
    }
}
