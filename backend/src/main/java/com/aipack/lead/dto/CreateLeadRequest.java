package com.aipack.lead.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLeadRequest(
        String source,
        String status,
        @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 255) String companyName,
        @Size(max = 64) String phone,
        @Min(0) @Max(100) Integer score,
        @Size(max = 8000) String summary) {}
