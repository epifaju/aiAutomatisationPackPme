package com.aipack.report;

import com.aipack.common.api.ApiResponse;
import com.aipack.config.WebhookAuthenticator;
import com.aipack.report.dto.DailyReportResponse;
import com.aipack.report.dto.WebhookDailyReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DailyReportWebhookController {

    static final String SECRET_HEADER = "X-Webhook-Secret";

    private final DailyReportService dailyReportService;
    private final WebhookAuthenticator webhookAuthenticator;

    public DailyReportWebhookController(
            DailyReportService dailyReportService, WebhookAuthenticator webhookAuthenticator) {
        this.dailyReportService = dailyReportService;
        this.webhookAuthenticator = webhookAuthenticator;
    }

    @PostMapping("/webhook/reports/daily")
    public ResponseEntity<ApiResponse<DailyReportResponse>> daily(
            @RequestHeader(value = SECRET_HEADER, required = false) String providedSecret,
            @Valid @RequestBody WebhookDailyReportRequest request) {
        webhookAuthenticator.requireValidSecret(providedSecret);
        DailyReportResponse report = dailyReportService.generateFromWebhook(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(report));
    }
}
