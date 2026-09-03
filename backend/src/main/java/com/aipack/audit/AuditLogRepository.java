package com.aipack.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    long countByCompany_IdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID companyId, String status, Instant from, Instant to);

    List<AuditLog> findTop10ByCompany_IdOrderByCreatedAtDesc(UUID companyId);

    long countByCompany_IdAndWorkflowInAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            UUID companyId, Collection<String> workflows, String status, Instant from, Instant to);
}
