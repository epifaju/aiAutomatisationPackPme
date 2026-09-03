package com.aipack.audit;

import java.util.Map;
import java.util.UUID;

public record AuditRecord(
        UUID companyId,
        String workflow,
        String action,
        String entityType,
        String entityId,
        AuditStatus status,
        Map<String, Object> metadata) {}
