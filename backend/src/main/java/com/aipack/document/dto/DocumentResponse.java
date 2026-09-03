package com.aipack.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
        UUID id,
        UUID companyId,
        String originalFilename,
        String contentType,
        String storageKey,
        long sizeBytes,
        String documentType,
        String status,
        String checksumSha256,
        DocumentExtractionResponse extraction,
        Instant createdAt,
        Instant updatedAt) {}
