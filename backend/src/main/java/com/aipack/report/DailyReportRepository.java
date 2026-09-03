package com.aipack.report;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DailyReportRepository
        extends JpaRepository<DailyReport, UUID>, JpaSpecificationExecutor<DailyReport> {

    Optional<DailyReport> findByIdAndCompany_Id(UUID id, UUID companyId);

    Optional<DailyReport> findByCompany_IdAndReportDate(UUID companyId, LocalDate reportDate);
}
