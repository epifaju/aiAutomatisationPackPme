package com.aipack.email.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailAnalysisResponse(
        UUID id,
        String category,
        String priority,
        String intent,
        String summary,
        String suggestedReply,
        BigDecimal confidenceScore,
        String status,
        String approvalStatus,
        Instant createdAt,
        Instant updatedAt) {}
