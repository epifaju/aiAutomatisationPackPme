package com.aipack.report;

import com.aipack.auth.AuthUser;
import com.aipack.common.api.ApiResponse;
import com.aipack.common.api.PageResponse;
import com.aipack.report.dto.DailyReportResponse;
import com.aipack.report.dto.GenerateDailyReportRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/daily")
public class DailyReportController {

    private final DailyReportService dailyReportService;

    public DailyReportController(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DailyReportResponse>> list(
            @AuthenticationPrincipal AuthUser principal,
            @RequestParam(required = false) LocalDate date,
            @PageableDefault(size = 20, sort = "reportDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(dailyReportService.list(principal.companyId(), date, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DailyReportResponse> get(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(dailyReportService.get(principal.companyId(), id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DailyReportResponse>> generate(
            @AuthenticationPrincipal AuthUser principal,
            @RequestBody(required = false) GenerateDailyReportRequest request) {
        DailyReportResponse created = dailyReportService.generate(principal.companyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PostMapping("/{id}/send")
    public ApiResponse<DailyReportResponse> send(@AuthenticationPrincipal AuthUser principal, @PathVariable UUID id) {
        return ApiResponse.ok(dailyReportService.send(principal.companyId(), id));
    }
}
