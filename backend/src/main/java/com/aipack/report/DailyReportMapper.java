package com.aipack.report;

import com.aipack.report.dto.DailyReportResponse;
import com.aipack.report.dto.ReportMetrics;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DailyReportMapper {

    @Mapping(target = "companyId", source = "company.id")
    @Mapping(target = "metrics", ignore = true)
    DailyReportResponse toResponse(DailyReport report);

    default DailyReportResponse toResponse(DailyReport report, ReportMetrics metrics) {
        DailyReportResponse base = toResponse(report);
        return new DailyReportResponse(
                base.id(),
                report.getCompany().getId(),
                base.reportDate(),
                metrics,
                base.summary(),
                base.status(),
                base.sentAt(),
                base.createdAt(),
                base.updatedAt());
    }
}
