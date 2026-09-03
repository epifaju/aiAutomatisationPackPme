package com.aipack.settings.dto;

import java.util.List;

public record AutomationResponse(
        String id,
        String name,
        String description,
        List<String> workflows,
        String historyPath,
        boolean enabled,
        boolean autoSendCompany,
        boolean autoSendEffective,
        boolean runnable,
        long executionsToday,
        long errorsToday) {}
