package com.aipack.settings;

import com.aipack.ai.AiProperties;
import com.aipack.config.EmailProperties;
import com.aipack.config.InvoiceProperties;
import com.aipack.config.ReportProperties;
import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import com.aipack.identity.CompanySettings;
import com.aipack.identity.CompanySettingsRepository;
import com.aipack.invoice.InvoiceOverdueCalculator;
import com.aipack.settings.dto.SettingsResponse;
import com.aipack.settings.dto.SettingsResponse.AiSettingsView;
import com.aipack.settings.dto.SettingsResponse.CompanySettingsView;
import com.aipack.settings.dto.SettingsResponse.DocumentSettingsView;
import com.aipack.settings.dto.SettingsResponse.EmailSettingsView;
import com.aipack.settings.dto.SettingsResponse.InvoiceSettingsView;
import com.aipack.settings.dto.SettingsResponse.LeadSettingsView;
import com.aipack.settings.dto.SettingsResponse.ReportSettingsView;
import com.aipack.settings.dto.SettingsResponse.SecuritySettingsView;
import com.aipack.settings.dto.UpdateSettingsRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final EntityManager entityManager;
    private final AiProperties aiProperties;
    private final EmailProperties emailProperties;
    private final InvoiceProperties invoiceProperties;
    private final ReportProperties reportProperties;

    public SettingsService(
            CompanyRepository companyRepository,
            CompanySettingsRepository companySettingsRepository,
            EntityManager entityManager,
            AiProperties aiProperties,
            EmailProperties emailProperties,
            InvoiceProperties invoiceProperties,
            ReportProperties reportProperties) {
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.entityManager = entityManager;
        this.aiProperties = aiProperties;
        this.emailProperties = emailProperties;
        this.invoiceProperties = invoiceProperties;
        this.reportProperties = reportProperties;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get(UUID companyId) {
        return toResponse(requireCompany(companyId), settingsOf(companyId));
    }

    @Transactional
    public SettingsResponse update(UUID companyId, UpdateSettingsRequest request) {
        Company company = requireCompany(companyId);
        CompanySettings settings = settingsOf(companyId);
        if (settings.getId() == null) {
            settings.setCompany(entityManager.getReference(Company.class, companyId));
        }
        apply(company, settings, request);
        companyRepository.save(company);
        companySettingsRepository.save(settings);
        return toResponse(company, settings);
    }

    CompanySettings settingsOf(UUID companyId) {
        return companySettingsRepository.findByCompanyId(companyId).orElseGet(SettingsService::defaults);
    }

    Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId).orElseThrow(SettingsException::companyNotFound);
    }

    private void apply(Company company, CompanySettings settings, UpdateSettingsRequest request) {
        if (request == null) {
            return;
        }
        if (request.company() != null) {
            if (request.company().name() != null && !request.company().name().isBlank()) {
                company.setName(request.company().name().trim());
            }
            if (request.company().siret() != null) {
                company.setSiret(request.company().siret().isBlank() ? null : request.company().siret().trim());
            }
            if (request.company().country() != null) {
                company.setCountry(request.company().country().trim().toUpperCase(Locale.ROOT));
            }
            if (request.company().timezone() != null) {
                company.setTimezone(requireTimezone(request.company().timezone()));
            }
        }
        if (request.email() != null) {
            if (request.email().autoSendCompany() != null) {
                settings.setAiGeneratedEmailAutoSend(request.email().autoSendCompany());
            }
            if (request.email().confidenceThreshold() != null) {
                settings.setEmailConfidenceThreshold(requireThreshold(request.email().confidenceThreshold()));
            }
        }
        if (request.ai() != null) {
            if (request.ai().provider() != null && !request.ai().provider().isBlank()) {
                settings.setAiProvider(request.ai().provider().trim().toUpperCase(Locale.ROOT));
            }
            if (request.ai().ollamaBaseUrl() != null && !request.ai().ollamaBaseUrl().isBlank()) {
                settings.setOllamaBaseUrl(request.ai().ollamaBaseUrl().trim());
            }
            if (request.ai().ollamaModel() != null && !request.ai().ollamaModel().isBlank()) {
                settings.setOllamaModel(request.ai().ollamaModel().trim());
            }
        }
        if (request.invoices() != null) {
            if (request.invoices().autoSendCompany() != null) {
                settings.setInvoiceReminderAutoSend(request.invoices().autoSendCompany());
            }
            if (request.invoices().reminderDays() != null) {
                List<Integer> levels = InvoiceOverdueCalculator.normalizeLevels(
                        request.invoices().reminderDays().toArray(Integer[]::new));
                settings.setInvoiceReminderDays(levels.toArray(Integer[]::new));
            }
        }
        if (request.reports() != null) {
            if (request.reports().autoSendCompany() != null) {
                settings.setDailyReportAutoSend(request.reports().autoSendCompany());
            }
            if (request.reports().dailyReportEmail() != null) {
                String email = request.reports().dailyReportEmail().trim();
                settings.setDailyReportEmail(email.isBlank() ? null : email);
            }
        }
        if (request.leads() != null) {
            int low = request.leads().scoreLowMax() == null ? settings.getLeadScoreLowMax() : request.leads().scoreLowMax();
            int medium = request.leads().scoreMediumMax() == null
                    ? settings.getLeadScoreMediumMax()
                    : request.leads().scoreMediumMax();
            int high = request.leads().scoreHighMax() == null ? settings.getLeadScoreHighMax() : request.leads().scoreHighMax();
            if (low < 0 || high > 100 || !(low < medium && medium < high)) {
                throw SettingsException.invalidScores();
            }
            settings.setLeadScoreLowMax(low);
            settings.setLeadScoreMediumMax(medium);
            settings.setLeadScoreHighMax(high);
            if (request.leads().confidenceThreshold() != null) {
                settings.setLeadConfidenceThreshold(requireThreshold(request.leads().confidenceThreshold()));
            }
        }
        if (request.documents() != null && request.documents().confidenceThreshold() != null) {
            settings.setDocumentConfidenceThreshold(requireThreshold(request.documents().confidenceThreshold()));
        }
        if (request.security() != null && request.security().dataRetentionDays() != null) {
            if (request.security().dataRetentionDays() < 1) {
                throw SettingsException.invalidRetention();
            }
            settings.setDataRetentionDays(request.security().dataRetentionDays());
        }
    }

    SettingsResponse toResponse(Company company, CompanySettings settings) {
        String runtimeUrl = aiProperties.ollama() == null ? "" : aiProperties.ollama().baseUrl();
        String runtimeModel = aiProperties.ollama() == null ? "" : aiProperties.ollama().model();
        boolean cacheEnabled = aiProperties.cache() != null && aiProperties.cache().enabled();
        List<Integer> days = InvoiceOverdueCalculator.normalizeLevels(settings.getInvoiceReminderDays());
        return new SettingsResponse(
                new CompanySettingsView(
                        company.getId(), company.getName(), company.getSiret(), company.getCountry(), company.getTimezone()),
                new EmailSettingsView(
                        emailProperties.autoSend(),
                        settings.isAiGeneratedEmailAutoSend(),
                        settings.getEmailConfidenceThreshold()),
                new AiSettingsView(
                        aiProperties.provider(),
                        runtimeUrl,
                        runtimeModel,
                        cacheEnabled,
                        settings.getAiProvider(),
                        settings.getOllamaBaseUrl(),
                        settings.getOllamaModel()),
                new InvoiceSettingsView(
                        invoiceProperties.autoSend(),
                        settings.isInvoiceReminderAutoSend(),
                        invoiceProperties.schedulerEnabled(),
                        days),
                new ReportSettingsView(
                        reportProperties.autoSend(),
                        settings.isDailyReportAutoSend(),
                        reportProperties.schedulerEnabled(),
                        settings.getDailyReportEmail()),
                new LeadSettingsView(
                        settings.getLeadScoreLowMax(),
                        settings.getLeadScoreMediumMax(),
                        settings.getLeadScoreHighMax(),
                        settings.getLeadConfidenceThreshold()),
                new DocumentSettingsView(settings.getDocumentConfidenceThreshold()),
                new SecuritySettingsView(settings.getDataRetentionDays()));
    }

    private static CompanySettings defaults() {
        return new CompanySettings();
    }

    private static String requireTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (RuntimeException ex) {
            throw SettingsException.invalidTimezone();
        }
    }

    private static BigDecimal requireThreshold(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw SettingsException.invalidThreshold();
        }
        return value;
    }
}
