package com.aipack.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID companyId,
        String workflow,
        String action,
        String entityType,
        String entityId,
        String status,
        Instant timestamp,
        Map<String, Object> metadata) {}
