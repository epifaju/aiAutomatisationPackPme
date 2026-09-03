package com.aipack.audit;

import com.aipack.audit.dto.AuditLogResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "timestamp", source = "createdAt")
    AuditLogResponse toResponse(AuditLog auditLog);
}
