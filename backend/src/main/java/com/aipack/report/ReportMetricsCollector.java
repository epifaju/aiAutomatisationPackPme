package com.aipack.report;

import com.aipack.audit.AuditLogRepository;
import com.aipack.audit.AuditStatus;
import com.aipack.document.DocumentRepository;
import com.aipack.document.DocumentStatus;
import com.aipack.email.EmailPriority;
import com.aipack.email.EmailRepository;
import com.aipack.identity.CompanySettings;
import com.aipack.invoice.InvoiceReminderRepository;
import com.aipack.invoice.InvoiceReminderStatus;
import com.aipack.invoice.InvoiceRepository;
import com.aipack.invoice.InvoiceStatus;
import com.aipack.lead.LeadRepository;
import com.aipack.report.dto.ReportMetrics;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReportMetricsCollector {

    private final EmailRepository emailRepository;
    private final LeadRepository leadRepository;
    private final DocumentRepository documentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceReminderRepository reminderRepository;
    private final AuditLogRepository auditLogRepository;

    public ReportMetricsCollector(
            EmailRepository emailRepository,
            LeadRepository leadRepository,
            DocumentRepository documentRepository,
            InvoiceRepository invoiceRepository,
            InvoiceReminderRepository reminderRepository,
            AuditLogRepository auditLogRepository) {
        this.emailRepository = emailRepository;
        this.leadRepository = leadRepository;
        this.documentRepository = documentRepository;
        this.invoiceRepository = invoiceRepository;
        this.reminderRepository = reminderRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public ReportMetrics collect(UUID companyId, ReportDayWindow window, CompanySettings settings) {
        int priorityMinScore = settings.getLeadScoreHighMax();
        long emailsReceived = emailRepository.countByCompany_IdAndReceivedAtGreaterThanEqualAndReceivedAtLessThan(
                companyId, window.from(), window.to());
        long emailsUrgent = emailRepository.countByCompanyAndPrioritiesInPeriod(
                companyId,
                List.of(EmailPriority.HIGH.name(), EmailPriority.URGENT.name()),
                window.from(),
                window.to());
        long newLeads = leadRepository.countByCompany_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                companyId, window.from(), window.to());
        long priorityLeads =
                leadRepository.countByCompany_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndScoreGreaterThan(
                        companyId, window.from(), window.to(), priorityMinScore);
        long documentsProcessed =
                documentRepository.countByCompany_IdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                        companyId, DocumentStatus.EXTRACTED.name(), window.from(), window.to());
        long documentsInError =
                documentRepository.countByCompany_IdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                        companyId, DocumentStatus.ERROR.name(), window.from(), window.to());
        long overdueInvoices = invoiceRepository.countByCompany_IdAndStatus(companyId, InvoiceStatus.OVERDUE.name());
        BigDecimal overdueAmount = invoiceRepository.sumAmountIncludingTaxByCompanyAndStatus(
                companyId, InvoiceStatus.OVERDUE.name());
        long remindersSent =
                reminderRepository.countByCompany_IdAndStatusAndSentAtGreaterThanEqualAndSentAtLessThan(
                        companyId, InvoiceReminderStatus.EXECUTED.name(), window.from(), window.to());
        long automationsExecuted =
                auditLogRepository.countByCompany_IdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        companyId, AuditStatus.SUCCESS.name(), window.from(), window.to());
        long automationErrors =
                auditLogRepository.countByCompany_IdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        companyId, AuditStatus.ERROR.name(), window.from(), window.to());
        return new ReportMetrics(
                emailsReceived,
                emailsUrgent,
                newLeads,
                priorityLeads,
                documentsProcessed,
                documentsInError,
                overdueInvoices,
                overdueAmount == null ? BigDecimal.ZERO : overdueAmount,
                "EUR",
                remindersSent,
                automationsExecuted,
                automationErrors);
    }
}
