package com.aipack.lead;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    Optional<Lead> findByIdAndCompany_Id(UUID id, UUID companyId);

    long countByCompany_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID companyId, Instant from, Instant to);

    long countByCompany_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndScoreGreaterThan(
            UUID companyId, Instant from, Instant to, int score);
}
