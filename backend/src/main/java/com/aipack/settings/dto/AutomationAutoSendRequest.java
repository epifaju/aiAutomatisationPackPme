package com.aipack.settings.dto;

import jakarta.validation.constraints.NotNull;

public record AutomationAutoSendRequest(@NotNull Boolean enabled) {}
