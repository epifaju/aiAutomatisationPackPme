package com.aipack.lead.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeadResponse(
        UUID id,
        UUID companyId,
        String source,
        String status,
        String email,
        String fullName,
        String companyName,
        String phone,
        int score,
        String scoreBand,
        String summary,
        String probableNeed,
        String urgency,
        String potentialBudget,
        String recommendedAction,
        String aiStatus,
        BigDecimal confidenceScore,
        Instant createdAt,
        Instant updatedAt,
        List<LeadEventResponse> events) {}
