package com.aipack.invoice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerSummary(UUID id, String name, String email) {}
