package com.aipack.config;

import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WebhookAuthenticator {

    private final CompanyRepository companyRepository;

    public WebhookAuthenticator(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Résout l’entreprise à partir du secret (hash unique). Le {@code companyId} du body
     * doit correspondre s’il est fourni — il n’est plus une source de confiance.
     */
    public UUID requireCompany(String providedSecret, UUID claimedCompanyId) {
        if (providedSecret == null || providedSecret.isBlank()) {
            throw new WebhookUnauthorizedException();
        }
        String hash = WebhookSecretHasher.sha256Hex(providedSecret);
        Company company = companyRepository
                .findByWebhookSecretHash(hash)
                .orElseThrow(WebhookUnauthorizedException::new);
        if (claimedCompanyId != null && !claimedCompanyId.equals(company.getId())) {
            throw new WebhookTenantMismatchException();
        }
        return company.getId();
    }
}
