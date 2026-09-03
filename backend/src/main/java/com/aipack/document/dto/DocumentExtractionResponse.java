package com.aipack.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentExtractionResponse(
        UUID id,
        Map<String, Object> extractedJson,
        BigDecimal confidenceScore,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
