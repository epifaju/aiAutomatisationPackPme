package com.aipack.settings;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.settings.dto.AutomationAutoSendRequest;
import com.aipack.settings.dto.AutomationResponse;
import com.aipack.settings.dto.AutomationRunResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automations")
public class AutomationController {

    private final AutomationService automationService;

    public AutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    @GetMapping
    public ApiResponse<List<AutomationResponse>> list(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(automationService.list(principal.companyId()));
    }

    @PostMapping("/{id}/run")
    public ApiResponse<AutomationRunResponse> run(
            @AuthenticationPrincipal AuthUser principal, @PathVariable String id) {
        return ApiResponse.ok(automationService.run(principal.companyId(), id));
    }

    @PostMapping("/{id}/auto-send")
    public ApiResponse<AutomationResponse> autoSend(
            @AuthenticationPrincipal AuthUser principal,
            @PathVariable String id,
            @Valid @RequestBody AutomationAutoSendRequest request) {
        return ApiResponse.ok(automationService.setAutoSend(principal.companyId(), id, request));
    }
}
