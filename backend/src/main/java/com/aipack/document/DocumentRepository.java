package com.aipack.document;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByIdAndCompany_Id(UUID id, UUID companyId);

    Optional<Document> findByCompany_IdAndChecksumSha256(UUID companyId, String checksumSha256);

    long countByCompany_IdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            UUID companyId, String status, Instant from, Instant to);
}
