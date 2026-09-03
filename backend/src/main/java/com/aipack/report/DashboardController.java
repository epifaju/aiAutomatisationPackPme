package com.aipack.report;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.report.dto.DashboardSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DailyReportService dailyReportService;

    public DashboardController(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary(@AuthenticationPrincipal AuthUser principal) {
        return ApiResponse.ok(dailyReportService.dashboard(principal.companyId()));
    }
}
