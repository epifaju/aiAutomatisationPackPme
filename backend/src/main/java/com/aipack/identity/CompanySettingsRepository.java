package com.aipack.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {

    Optional<CompanySettings> findByCompanyId(UUID companyId);
}
