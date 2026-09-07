package com.aipack.audit;

import com.aipack.audit.dto.AuditLogResponse;
import com.aipack.audit.dto.WebhookN8nErrorRequest;
import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final int MAX_ERROR_MESSAGE_CHARS = 2_000;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final EntityManager entityManager;
    private final CompanyRepository companyRepository;

    public AuditService(
            AuditLogRepository auditLogRepository,
            AuditLogMapper auditLogMapper,
            EntityManager entityManager,
            CompanyRepository companyRepository) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
        this.entityManager = entityManager;
        this.companyRepository = companyRepository;
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

    @Transactional
    public AuditLogResponse recordN8nError(WebhookN8nErrorRequest request) {
        if (!companyRepository.existsById(request.companyId())) {
            throw AuditException.companyNotFound();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        if (request.errorType() != null && !request.errorType().isBlank()) {
            metadata.putIfAbsent("errorType", truncate(request.errorType().trim(), 128));
        }
        if (request.errorMessage() != null && !request.errorMessage().isBlank()) {
            metadata.putIfAbsent("errorMessage", truncate(request.errorMessage().trim(), MAX_ERROR_MESSAGE_CHARS));
        }
        if (request.execution() != null && !request.execution().isBlank()) {
            metadata.putIfAbsent("executionId", truncate(request.execution().trim(), 128));
        }
        String workflow = blankToNull(request.workflow());
        if (workflow == null) {
            workflow = "n8n";
        } else if (workflow.length() > MAX_WORKFLOW_LENGTH) {
            workflow = workflow.substring(0, MAX_WORKFLOW_LENGTH);
        }
        String entityId = blankToNull(request.execution());
        if (entityId != null && entityId.length() > MAX_ENTITY_ID_LENGTH) {
            entityId = entityId.substring(0, MAX_ENTITY_ID_LENGTH);
        }
        return record(new AuditRecord(
                request.companyId(),
                workflow,
                "N8N_WORKFLOW_ERROR",
                "WORKFLOW",
                entityId,
                AuditStatus.ERROR,
                metadata));
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

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
