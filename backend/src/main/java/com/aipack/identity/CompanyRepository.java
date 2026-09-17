package com.aipack.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByWebhookSecretHash(String webhookSecretHash);

    Optional<Company> findFirstByWebhookSecretHashIsNull();

    boolean existsByWebhookSecretHash(String webhookSecretHash);
}
