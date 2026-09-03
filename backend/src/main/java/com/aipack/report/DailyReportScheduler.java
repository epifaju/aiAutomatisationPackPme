package com.aipack.report;

import com.aipack.config.ReportProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.reports", name = "scheduler-enabled", havingValue = "true")
public class DailyReportScheduler {

    private final DailyReportService dailyReportService;
    private final ReportProperties reportProperties;

    public DailyReportScheduler(DailyReportService dailyReportService, ReportProperties reportProperties) {
        this.dailyReportService = dailyReportService;
        this.reportProperties = reportProperties;
    }

    @Scheduled(cron = "${app.reports.scheduler-cron:0 30 7 * * *}", zone = "Europe/Paris")
    public void generateYesterday() {
        if (!reportProperties.schedulerEnabled()) {
            return;
        }
        dailyReportService.generateYesterdayForAllCompanies();
    }
}
