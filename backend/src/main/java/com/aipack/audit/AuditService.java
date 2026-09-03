package com.aipack.audit;

import com.aipack.audit.dto.AuditLogResponse;
import com.aipack.identity.Company;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final int MAX_WORKFLOW_LENGTH = 128;
    private static final int MAX_ACTION_LENGTH = 64;
    private static final int MAX_ENTITY_TYPE_LENGTH = 64;
    private static final int MAX_ENTITY_ID_LENGTH = 64;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final EntityManager entityManager;

    public AuditService(
            AuditLogRepository auditLogRepository, AuditLogMapper auditLogMapper, EntityManager entityManager) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public AuditLogResponse record(AuditRecord record) {
        validate(record);
        AuditLog log = new AuditLog();
        log.setCompany(entityManager.getReference(Company.class, record.companyId()));
        log.setWorkflow(blankToNull(record.workflow()));
        log.setAction(record.action().trim());
        log.setEntityType(record.entityType().trim());
        log.setEntityId(blankToNull(record.entityId()));
        log.setStatus(record.status().name());
        log.setMetadata(MetadataSanitizer.sanitize(record.metadata()));
        return auditLogMapper.toResponse(auditLogRepository.save(log));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(
            UUID companyId,
            String workflow,
            String action,
            String entityType,
            String entityId,
            String status,
            Instant from,
            Instant to,
            Pageable pageable) {
        String statusFilter = normalizeStatus(status);
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (workflow != null && !workflow.isBlank()) {
                predicates.add(cb.equal(root.get("workflow"), workflow.trim()));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return auditLogRepository.findAll(spec, pageable).map(auditLogMapper::toResponse);
    }

    private static void validate(AuditRecord record) {
        if (record.companyId() == null) {
            throw new IllegalArgumentException("companyId is required");
        }
        if (record.status() == null) {
            throw new IllegalArgumentException("status is required");
        }
        requireLength("action", record.action(), MAX_ACTION_LENGTH);
        requireLength("entityType", record.entityType(), MAX_ENTITY_TYPE_LENGTH);
        if (record.workflow() != null && record.workflow().length() > MAX_WORKFLOW_LENGTH) {
            throw new IllegalArgumentException("workflow exceeds " + MAX_WORKFLOW_LENGTH + " characters");
        }
        if (record.entityId() != null && record.entityId().length() > MAX_ENTITY_ID_LENGTH) {
            throw new IllegalArgumentException("entityId exceeds " + MAX_ENTITY_ID_LENGTH + " characters");
        }
    }

    private static void requireLength(String field, String value, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.trim().length() > max) {
            throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            return AuditStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw AuditException.invalidStatus();
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
