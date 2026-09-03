package com.aipack.email;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailRepository extends JpaRepository<Email, UUID>, JpaSpecificationExecutor<Email> {

    Optional<Email> findByIdAndCompany_Id(UUID id, UUID companyId);

    Optional<Email> findByCompany_IdAndMessageId(UUID companyId, String messageId);

    long countByCompany_IdAndReceivedAtGreaterThanEqualAndReceivedAtLessThan(
            UUID companyId, Instant from, Instant to);

    @Query(
            """
            SELECT count(a) FROM EmailAnalysis a
            WHERE a.company.id = :companyId
              AND a.priority IN :priorities
              AND a.email.receivedAt >= :from AND a.email.receivedAt < :to
            """)
    long countByCompanyAndPrioritiesInPeriod(
            @Param("companyId") UUID companyId,
            @Param("priorities") Collection<String> priorities,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
