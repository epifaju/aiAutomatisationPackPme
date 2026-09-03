package com.aipack.report;

import com.aipack.ai.AIRequest;
import com.aipack.ai.AIResponse;
import com.aipack.ai.AiGateway;
import com.aipack.ai.AiParsingException;
import com.aipack.ai.PromptCatalog;
import com.aipack.audit.AuditLogMapper;
import com.aipack.audit.AuditLogRepository;
import com.aipack.audit.AuditRecord;
import com.aipack.audit.AuditService;
import com.aipack.audit.AuditStatus;
import com.aipack.audit.dto.AuditLogResponse;
import com.aipack.config.ReportProperties;
import com.aipack.email.OutboundMail;
import com.aipack.email.OutboundMailSender;
import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import com.aipack.identity.CompanySettings;
import com.aipack.identity.CompanySettingsRepository;
import com.aipack.identity.User;
import com.aipack.identity.UserRepository;
import com.aipack.report.dto.DailyReportResponse;
import com.aipack.report.dto.DashboardSummaryResponse;
import com.aipack.report.dto.GenerateDailyReportRequest;
import com.aipack.report.dto.ReportMetrics;
import com.aipack.report.dto.WebhookDailyReportRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyReportService {

    private static final Logger log = LoggerFactory.getLogger(DailyReportService.class);

    static final String WORKFLOW = "daily-report";
    static final String ENTITY_TYPE = "REPORT";

    private final DailyReportRepository reportRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final ReportMetricsCollector metricsCollector;
    private final DailyReportComposer composer;
    private final DailyReportSummaryParser summaryParser;
    private final DailyReportMapper reportMapper;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final AiGateway aiGateway;
    private final PromptCatalog promptCatalog;
    private final OutboundMailSender outboundMailSender;
    private final ReportProperties reportProperties;
    private final Clock clock;

    public DailyReportService(
            DailyReportRepository reportRepository,
            CompanyRepository companyRepository,
            CompanySettingsRepository companySettingsRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            AuditLogMapper auditLogMapper,
            ReportMetricsCollector metricsCollector,
            DailyReportComposer composer,
            DailyReportSummaryParser summaryParser,
            DailyReportMapper reportMapper,
            AuditService auditService,
            EntityManager entityManager,
            AiGateway aiGateway,
            PromptCatalog promptCatalog,
            OutboundMailSender outboundMailSender,
            ReportProperties reportProperties,
            Clock clock) {
        this.reportRepository = reportRepository;
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
        this.metricsCollector = metricsCollector;
        this.composer = composer;
        this.summaryParser = summaryParser;
        this.reportMapper = reportMapper;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.aiGateway = aiGateway;
        this.promptCatalog = promptCatalog;
        this.outboundMailSender = outboundMailSender;
        this.reportProperties = reportProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse dashboard(UUID companyId) {
        Company company = requireCompany(companyId);
        CompanySettings settings = settingsOf(companyId);
        ReportDayWindow window = ReportDayWindow.of(ReportDayWindow.today(company.getTimezone(), clock), company.getTimezone());
        ReportMetrics metrics = metricsCollector.collect(companyId, window, settings);
        List<AuditLogResponse> recent = auditLogRepository.findTop10ByCompany_IdOrderByCreatedAtDesc(companyId).stream()
                .map(auditLogMapper::toResponse)
                .toList();
        return new DashboardSummaryResponse(window.date(), metrics, recent);
    }

    @Transactional(readOnly = true)
    public Page<DailyReportResponse> list(UUID companyId, LocalDate date, Pageable pageable) {
        Specification<DailyReport> spec = (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (date != null) {
                predicates.add(cb.equal(root.get("reportDate"), date));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return reportRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DailyReportResponse get(UUID companyId, UUID id) {
        return toResponse(requireReport(companyId, id));
    }

    @Transactional
    public DailyReportResponse generate(UUID companyId, GenerateDailyReportRequest request) {
        boolean send = request != null && Boolean.TRUE.equals(request.send());
        LocalDate date = request == null ? null : request.date();
        return generate(companyId, date, send, false);
    }

    @Transactional
    public DailyReportResponse generateFromWebhook(WebhookDailyReportRequest request) {
        boolean send = Boolean.TRUE.equals(request.send());
        return generate(request.companyId(), request.date(), send, false);
    }

    @Transactional
    public void generateYesterdayForAllCompanies() {
        for (Company company : companyRepository.findAll()) {
            try {
                LocalDate yesterday = ReportDayWindow.yesterday(company.getTimezone(), clock);
                CompanySettings settings = settingsOf(company.getId());
                boolean send = autoSendEnabled(settings);
                generate(company.getId(), yesterday, send, true);
            } catch (RuntimeException ex) {
                log.warn("Rapport quotidien en échec (company={}): {}", company.getId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public DailyReportResponse send(UUID companyId, UUID id) {
        DailyReport report = requireReport(companyId, id);
        return dispatchSend(report, settingsOf(companyId), false);
    }

    private DailyReportResponse generate(UUID companyId, LocalDate requestedDate, boolean send, boolean scheduled) {
        Company company = requireCompany(companyId);
        CompanySettings settings = settingsOf(companyId);
        LocalDate date = requestedDate == null ? ReportDayWindow.today(company.getTimezone(), clock) : requestedDate;
        ReportDayWindow window = ReportDayWindow.of(date, company.getTimezone());
        ReportMetrics metrics = metricsCollector.collect(companyId, window, settings);
        String summary = summarize(company, window, metrics);
        DailyReport report = reportRepository
                .findByCompany_IdAndReportDate(companyId, date)
                .orElseGet(() -> {
                    DailyReport created = new DailyReport();
                    created.setCompany(entityManager.getReference(Company.class, companyId));
                    created.setReportDate(date);
                    return created;
                });
        report.setMetrics(composer.toMap(metrics));
        report.setSummary(summary);
        if (!DailyReportStatus.SENT.name().equals(report.getStatus())) {
            report.setStatus(DailyReportStatus.GENERATED.name());
        }
        DailyReport saved = reportRepository.save(report);
        audit(
                companyId,
                "GENERATED",
                saved.getId(),
                AuditStatus.SUCCESS,
                Map.of("reportDate", date.toString(), "scheduled", scheduled));
        if (send) {
            return dispatchSend(saved, settings, scheduled);
        }
        return reportMapper.toResponse(saved, metrics);
    }

    private String summarize(Company company, ReportDayWindow window, ReportMetrics metrics) {
        String fallback = composer.compose(company.getName(), window, metrics);
        AIResponse response = aiGateway.generate(
                new AIRequest(company.getId(), "daily-report", promptCatalog.dailyReport(composer.promptVariables(company.getName(), window, metrics))));
        if (!response.isSuccess()) {
            return fallback;
        }
        try {
            return summaryParser.parse(response.text());
        } catch (AiParsingException ex) {
            return fallback;
        }
    }

    private DailyReportResponse dispatchSend(DailyReport report, CompanySettings settings, boolean automatic) {
        String recipient = recipientOf(report.getCompany().getId(), settings);
        if (recipient == null) {
            report.setStatus(DailyReportStatus.ERROR.name());
            audit(
                    report.getCompany().getId(),
                    "EMAIL_SENT",
                    report.getId(),
                    AuditStatus.ERROR,
                    Map.of("reason", "MISSING_RECIPIENT", "automatic", automatic));
            if (automatic) {
                return reportMapper.toResponse(report, composer.fromMap(report.getMetrics()));
            }
            throw ReportException.missingRecipient();
        }
        ReportMetrics metrics = composer.fromMap(report.getMetrics());
        String body = report.getSummary() == null || report.getSummary().isBlank()
                ? composer.compose(report.getCompany().getName(), ReportDayWindow.of(report.getReportDate(), report.getCompany().getTimezone()), metrics)
                : report.getSummary();
        try {
            outboundMailSender.send(new OutboundMail(
                    reportProperties.fromAddressOrDefault(),
                    recipient,
                    "Rapport quotidien — " + report.getReportDate(),
                    body));
        } catch (RuntimeException ex) {
            report.setStatus(DailyReportStatus.ERROR.name());
            audit(
                    report.getCompany().getId(),
                    "EMAIL_SENT",
                    report.getId(),
                    AuditStatus.ERROR,
                    Map.of("automatic", automatic));
            if (automatic) {
                return reportMapper.toResponse(report, composer.fromMap(report.getMetrics()));
            }
            throw ReportException.mailSendFailed();
        }
        report.setStatus(DailyReportStatus.SENT.name());
        report.setSentAt(Instant.now(clock));
        audit(
                report.getCompany().getId(),
                "EMAIL_SENT",
                report.getId(),
                AuditStatus.SUCCESS,
                Map.of("automatic", automatic, "reportDate", report.getReportDate().toString()));
        return reportMapper.toResponse(report, metrics);
    }

    private String recipientOf(UUID companyId, CompanySettings settings) {
        if (settings.getDailyReportEmail() != null && !settings.getDailyReportEmail().isBlank()) {
            return settings.getDailyReportEmail().trim();
        }
        return userRepository
                .findFirstByCompany_IdAndRoleAndEnabledTrueOrderByCreatedAtAsc(companyId, "ADMIN")
                .or(() -> userRepository.findFirstByCompany_IdAndEnabledTrueOrderByCreatedAtAsc(companyId))
                .map(User::getEmail)
                .orElse(null);
    }

    private boolean autoSendEnabled(CompanySettings settings) {
        return reportProperties.autoSend() && settings.isDailyReportAutoSend();
    }

    private Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(ReportException::companyNotFound);
    }

    private DailyReport requireReport(UUID companyId, UUID id) {
        return reportRepository.findByIdAndCompany_Id(id, companyId).orElseThrow(ReportException::notFound);
    }

    private CompanySettings settingsOf(UUID companyId) {
        return companySettingsRepository.findByCompanyId(companyId).orElseGet(() -> {
            CompanySettings defaults = new CompanySettings();
            defaults.setLeadScoreHighMax(80);
            defaults.setDailyReportAutoSend(false);
            return defaults;
        });
    }

    private DailyReportResponse toResponse(DailyReport report) {
        return reportMapper.toResponse(report, composer.fromMap(report.getMetrics()));
    }

    private void audit(UUID companyId, String action, UUID entityId, AuditStatus status, Map<String, Object> metadata) {
        auditService.record(new AuditRecord(
                companyId, WORKFLOW, action, ENTITY_TYPE, entityId.toString(), status, metadata));
    }
}
