package com.aipack.settings;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.settings.dto.SettingsResponse;
import com.aipack.settings.dto.UpdateSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<SettingsResponse> get(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(settingsService.get(principal.companyId()));
    }

    @PutMapping
    public ApiResponse<SettingsResponse> update(
            @AuthenticationPrincipal AuthUser principal, @Valid @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.ok(settingsService.update(principal.companyId(), request));
    }
}
