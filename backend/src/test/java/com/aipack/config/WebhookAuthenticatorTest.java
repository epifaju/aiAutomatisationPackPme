package com.aipack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookAuthenticatorTest {

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002");

    @Mock
    private CompanyRepository companyRepository;

    private WebhookAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new WebhookAuthenticator(companyRepository);
    }

    @Test
    void resolvesCompanyFromSecretHash() {
        Company company = new Company();
        company.setId(COMPANY_A);
        String secret = "tenant-a-secret-16";
        when(companyRepository.findByWebhookSecretHash(WebhookSecretHasher.sha256Hex(secret)))
                .thenReturn(Optional.of(company));

        assertThat(authenticator.requireCompany(secret, COMPANY_A)).isEqualTo(COMPANY_A);
        assertThat(authenticator.requireCompany(secret, null)).isEqualTo(COMPANY_A);
    }

    @Test
    void rejectsClaimedCompanyIdThatDoesNotMatchSecret() {
        Company company = new Company();
        company.setId(COMPANY_A);
        String secret = "tenant-a-secret-16";
        when(companyRepository.findByWebhookSecretHash(WebhookSecretHasher.sha256Hex(secret)))
                .thenReturn(Optional.of(company));

        assertThatThrownBy(() -> authenticator.requireCompany(secret, COMPANY_B))
                .isInstanceOf(WebhookTenantMismatchException.class);
    }

    @Test
    void rejectsUnknownSecret() {
        when(companyRepository.findByWebhookSecretHash(WebhookSecretHasher.sha256Hex("nope")))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> authenticator.requireCompany("nope", COMPANY_A))
                .isInstanceOf(WebhookUnauthorizedException.class);
        assertThatThrownBy(() -> authenticator.requireCompany(null, COMPANY_A))
                .isInstanceOf(WebhookUnauthorizedException.class);
    }
}
