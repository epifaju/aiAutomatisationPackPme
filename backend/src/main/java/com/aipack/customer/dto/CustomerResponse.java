package com.aipack.customer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse(
        UUID id,
        UUID companyId,
        String name,
        String email,
        String phone,
        String address,
        Instant createdAt,
        Instant updatedAt) {}
