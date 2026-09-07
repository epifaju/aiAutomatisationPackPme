package com.aipack.email.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailResponse(
        UUID id,
        UUID companyId,
        String messageId,
        String fromAddress,
        String toAddress,
        String subject,
        String bodyText,
        Instant receivedAt,
        String status,
        EmailAnalysisResponse analysis,
        List<EmailAttachmentResponse> attachments,
        boolean autoSendEnabled,
        Instant createdAt,
        Instant updatedAt) {}
