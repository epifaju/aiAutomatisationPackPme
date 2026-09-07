package com.aipack.lead.dto;

import java.util.List;
import java.util.UUID;

public record LeadImportResponse(
        int imported, int failed, int totalRows, List<UUID> leadIds, List<LeadImportError> errors) {}
