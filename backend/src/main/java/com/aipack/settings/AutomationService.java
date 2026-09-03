package com.aipack.settings;

import com.aipack.audit.AuditLogRepository;
import com.aipack.audit.AuditStatus;
import com.aipack.config.EmailProperties;
import com.aipack.config.InvoiceProperties;
import com.aipack.config.ReportProperties;
import com.aipack.identity.Company;
import com.aipack.identity.CompanySettings;
import com.aipack.invoice.InvoiceService;
import com.aipack.invoice.dto.OverdueDetectionResponse;
import com.aipack.report.DailyReportService;
import com.aipack.report.ReportDayWindow;
import com.aipack.report.dto.GenerateDailyReportRequest;
import com.aipack.settings.dto.AutomationAutoSendRequest;
import com.aipack.settings.dto.AutomationResponse;
import com.aipack.settings.dto.AutomationRunResponse;
import com.aipack.settings.dto.UpdateSettingsRequest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationService {

    public static final String EMAIL = "email-assistant";
    public static final String LEADS = "lead-qualification";
    public static final String DOCUMENTS = "document-ai";
    public static final String INVOICES = "invoice-reminder";
    public static final String REPORTS = "daily-report";

    private static final List<String> EMAIL_WORKFLOWS =
            List.of("email-ingestion", "email-ai-analysis", "email-reply-generation");
    private static final List<String> LEAD_WORKFLOWS = List.of("lead-capture", "lead-qualification");
    private static final List<String> DOCUMENT_WORKFLOWS = List.of("document-ingestion", "document-ai-extraction");
    private static final List<String> INVOICE_WORKFLOWS =
            List.of("invoice", "invoice-overdue-detection", "invoice-reminder");
    private static final List<String> REPORT_WORKFLOWS = List.of("daily-report");

    private final SettingsService settingsService;
    private final AuditLogRepository auditLogRepository;
    private final InvoiceService invoiceService;
    private final DailyReportService dailyReportService;
    private final EmailProperties emailProperties;
    private final InvoiceProperties invoiceProperties;
    private final ReportProperties reportProperties;
    private final Clock clock;

    public AutomationService(
            SettingsService settingsService,
            AuditLogRepository auditLogRepository,
            InvoiceService invoiceService,
            DailyReportService dailyReportService,
            EmailProperties emailProperties,
            InvoiceProperties invoiceProperties,
            ReportProperties reportProperties,
            Clock clock) {
        this.settingsService = settingsService;
        this.auditLogRepository = auditLogRepository;
        this.invoiceService = invoiceService;
        this.dailyReportService = dailyReportService;
        this.emailProperties = emailProperties;
        this.invoiceProperties = invoiceProperties;
        this.reportProperties = reportProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AutomationResponse> list(UUID companyId) {
        Company company = settingsService.requireCompany(companyId);
        CompanySettings settings = settingsService.settingsOf(companyId);
        ReportDayWindow window = ReportDayWindow.of(ReportDayWindow.today(company.getTimezone(), clock), company.getTimezone());
        return List.of(
                card(
                        EMAIL,
                        "Assistant Email",
                        "Classifie les emails et prépare une réponse. L'envoi reste soumis à validation humaine.",
                        EMAIL_WORKFLOWS,
                        "/audit?workflow=email-ai-analysis",
                        true,
                        settings.isAiGeneratedEmailAutoSend(),
                        emailProperties.autoSend() && settings.isAiGeneratedEmailAutoSend(),
                        false,
                        companyId,
                        window),
                card(
                        LEADS,
                        "Qualification prospects",
                        "Score et résumé IA des nouveaux leads. Toujours déclenchée à la demande.",
                        LEAD_WORKFLOWS,
                        "/audit?workflow=lead-qualification",
                        true,
                        false,
                        false,
                        false,
                        companyId,
                        window),
                card(
                        DOCUMENTS,
                        "Document AI",
                        "Extraction des champs depuis PDF, images et textes. Revue si la confiance est basse.",
                        DOCUMENT_WORKFLOWS,
                        "/audit?workflow=document-ai-extraction",
                        true,
                        false,
                        false,
                        false,
                        companyId,
                        window),
                card(
                        INVOICES,
                        "Relances factures",
                        "Détecte les retards et prépare les relances J+3 / J+7 / J+15 / J+30.",
                        INVOICE_WORKFLOWS,
                        "/audit?workflow=invoice-reminder",
                        true,
                        settings.isInvoiceReminderAutoSend(),
                        invoiceProperties.autoSend() && settings.isInvoiceReminderAutoSend(),
                        true,
                        companyId,
                        window),
                card(
                        REPORTS,
                        "Rapport quotidien",
                        "Synthèse d'activité et envoi email. Le scheduler cible la veille à 07:30.",
                        REPORT_WORKFLOWS,
                        "/audit?workflow=daily-report",
                        true,
                        settings.isDailyReportAutoSend(),
                        reportProperties.autoSend() && settings.isDailyReportAutoSend(),
                        true,
                        companyId,
                        window));
    }

    @Transactional
    public AutomationRunResponse run(UUID companyId, String id) {
        return switch (id) {
            case INVOICES -> {
                OverdueDetectionResponse detection = invoiceService.detectOverdue(companyId, null);
                yield new AutomationRunResponse(
                        id,
                        detection.invoicesMarkedOverdue()
                                + " facture(s) en retard, "
                                + detection.remindersCreated()
                                + " relance(s) créée(s).");
            }
            case REPORTS -> {
                dailyReportService.generate(companyId, new GenerateDailyReportRequest(null, false));
                yield new AutomationRunResponse(id, "Rapport du jour généré.");
            }
            case EMAIL, LEADS, DOCUMENTS -> throw SettingsException.cannotRun();
            default -> throw SettingsException.unknownAutomation();
        };
    }

    @Transactional
    public AutomationResponse setAutoSend(UUID companyId, String id, AutomationAutoSendRequest request) {
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        switch (id) {
            case EMAIL -> settingsService.update(
                    companyId,
                    new UpdateSettingsRequest(
                            null,
                            new UpdateSettingsRequest.EmailPatch(enabled, null),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
            case INVOICES -> settingsService.update(
                    companyId,
                    new UpdateSettingsRequest(
                            null,
                            null,
                            null,
                            new UpdateSettingsRequest.InvoicePatch(enabled, null),
                            null,
                            null,
                            null,
                            null));
            case REPORTS -> settingsService.update(
                    companyId,
                    new UpdateSettingsRequest(
                            null,
                            null,
                            null,
                            null,
                            new UpdateSettingsRequest.ReportPatch(enabled, null),
                            null,
                            null,
                            null));
            case LEADS, DOCUMENTS -> throw SettingsException.autoSendNotSupported();
            default -> throw SettingsException.unknownAutomation();
        }
        return list(companyId).stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(SettingsException::unknownAutomation);
    }

    private AutomationResponse card(
            String id,
            String name,
            String description,
            List<String> workflows,
            String historyPath,
            boolean enabled,
            boolean autoSendCompany,
            boolean autoSendEffective,
            boolean runnable,
            UUID companyId,
            ReportDayWindow window) {
        long executions = auditLogRepository.countByCompany_IdAndWorkflowInAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                companyId, workflows, AuditStatus.SUCCESS.name(), window.from(), window.to());
        long errors = auditLogRepository.countByCompany_IdAndWorkflowInAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                companyId, workflows, AuditStatus.ERROR.name(), window.from(), window.to());
        return new AutomationResponse(
                id,
                name,
                description,
                workflows,
                historyPath,
                enabled,
                autoSendCompany,
                autoSendEffective,
                runnable,
                executions,
                errors);
    }
}
