package com.aipack.lead;

import com.aipack.ai.AIRequest;
import com.aipack.ai.AIResponse;
import com.aipack.ai.AiGateway;
import com.aipack.ai.AiParsingException;
import com.aipack.ai.PromptCatalog;
import com.aipack.audit.AuditRecord;
import com.aipack.audit.AuditService;
import com.aipack.audit.AuditStatus;
import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import com.aipack.identity.CompanySettings;
import com.aipack.identity.CompanySettingsRepository;
import com.aipack.lead.dto.CreateLeadRequest;
import com.aipack.lead.dto.LeadEventResponse;
import com.aipack.lead.dto.LeadResponse;
import com.aipack.lead.dto.UpdateLeadRequest;
import com.aipack.lead.dto.WebhookLeadRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

    static final String WORKFLOW_CAPTURE = "lead-capture";
    static final String WORKFLOW_QUALIFICATION = "lead-qualification";
    static final String ENTITY_TYPE = "LEAD";

    private final LeadRepository leadRepository;
    private final LeadEventRepository leadEventRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final LeadMapper leadMapper;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final AiGateway aiGateway;
    private final PromptCatalog promptCatalog;
    private final LeadQualificationParser qualificationParser;

    public LeadService(
            LeadRepository leadRepository,
            LeadEventRepository leadEventRepository,
            CompanyRepository companyRepository,
            CompanySettingsRepository companySettingsRepository,
            LeadMapper leadMapper,
            AuditService auditService,
            EntityManager entityManager,
            AiGateway aiGateway,
            PromptCatalog promptCatalog,
            LeadQualificationParser qualificationParser) {
        this.leadRepository = leadRepository;
        this.leadEventRepository = leadEventRepository;
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.leadMapper = leadMapper;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.aiGateway = aiGateway;
        this.promptCatalog = promptCatalog;
        this.qualificationParser = qualificationParser;
    }

    @Transactional(readOnly = true)
    public Page<LeadResponse> list(UUID companyId, String status, String source, String q, Pageable pageable) {
        String statusFilter = blankToNull(status) == null ? null : LeadStatus.from(status).name();
        String sourceFilter = blankToNull(source) == null ? null : LeadSource.from(source).name();
        String query = blankToNull(q);
        Specification<Lead> spec = (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (sourceFilter != null) {
                predicates.add(cb.equal(root.get("source"), sourceFilter));
            }
            if (query != null) {
                String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("companyName"), "")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        CompanySettings settings = settingsOf(companyId);
        return leadRepository.findAll(spec, pageable).map(lead -> toResponse(lead, settings, false));
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID companyId, UUID id) {
        Lead lead = requireLead(companyId, id);
        return toResponse(lead, settingsOf(companyId), true);
    }

    @Transactional
    public LeadResponse create(UUID companyId, CreateLeadRequest request) {
        Lead lead = new Lead();
        lead.setCompany(entityManager.getReference(Company.class, companyId));
        lead.setSource(request.source() == null || request.source().isBlank()
                ? LeadSource.API.name()
                : LeadSource.from(request.source()).name());
        lead.setStatus(request.status() == null || request.status().isBlank()
                ? LeadStatus.NEW.name()
                : LeadStatus.from(request.status()).name());
        lead.setEmail(blankToNull(request.email()));
        lead.setFullName(request.fullName().trim());
        lead.setCompanyName(blankToNull(request.companyName()));
        lead.setPhone(blankToNull(request.phone()));
        lead.setScore(request.score() == null ? 0 : request.score());
        lead.setSummary(blankToNull(request.summary()));
        Lead saved = leadRepository.save(lead);
        addEvent(saved, "CREATED", Map.of("source", saved.getSource()));
        audit(companyId, WORKFLOW_CAPTURE, "CREATED", saved.getId(), AuditStatus.SUCCESS, Map.of("source", saved.getSource()));
        return toResponse(saved, settingsOf(companyId), true);
    }

    @Transactional
    public LeadResponse update(UUID companyId, UUID id, UpdateLeadRequest request) {
        Lead lead = requireLead(companyId, id);
        String previousStatus = lead.getStatus();
        if (request.source() != null && !request.source().isBlank()) {
            lead.setSource(LeadSource.from(request.source()).name());
        }
        if (request.status() != null && !request.status().isBlank()) {
            lead.setStatus(LeadStatus.from(request.status()).name());
        }
        if (request.email() != null) {
            lead.setEmail(blankToNull(request.email()));
        }
        if (request.fullName() != null) {
            if (request.fullName().isBlank()) {
                throw LeadException.invalidName();
            }
            lead.setFullName(request.fullName().trim());
        }
        if (request.companyName() != null) {
            lead.setCompanyName(blankToNull(request.companyName()));
        }
        if (request.phone() != null) {
            lead.setPhone(blankToNull(request.phone()));
        }
        if (request.score() != null) {
            lead.setScore(request.score());
        }
        if (request.summary() != null) {
            lead.setSummary(blankToNull(request.summary()));
        }
        if (request.probableNeed() != null) {
            lead.setProbableNeed(blankToNull(request.probableNeed()));
        }
        if (request.urgency() != null) {
            lead.setUrgency(normalizeUrgency(request.urgency()));
        }
        if (request.potentialBudget() != null) {
            lead.setPotentialBudget(blankToNull(request.potentialBudget()));
        }
        if (request.recommendedAction() != null) {
            lead.setRecommendedAction(blankToNull(request.recommendedAction()));
        }
        if (!previousStatus.equals(lead.getStatus())) {
            addEvent(lead, "STATUS_CHANGED", Map.of("from", previousStatus, "to", lead.getStatus()));
        }
        audit(companyId, WORKFLOW_CAPTURE, "UPDATED", lead.getId(), AuditStatus.SUCCESS, Map.of("status", lead.getStatus()));
        return toResponse(lead, settingsOf(companyId), true);
    }

    @Transactional
    public void delete(UUID companyId, UUID id) {
        Lead lead = requireLead(companyId, id);
        leadRepository.delete(lead);
        audit(companyId, WORKFLOW_CAPTURE, "DELETED", id, AuditStatus.SUCCESS, Map.of("fullName", lead.getFullName()));
    }

    @Transactional
    public LeadResponse captureFromWebhook(WebhookLeadRequest request, boolean qualify) {
        if (!companyRepository.existsById(request.companyId())) {
            throw LeadException.companyNotFound();
        }
        CreateLeadRequest createRequest = new CreateLeadRequest(
                LeadSource.WEBHOOK.name(),
                LeadStatus.NEW.name(),
                request.email(),
                request.fullName(),
                request.companyName(),
                request.phone(),
                0,
                request.summary());
        LeadResponse created = create(request.companyId(), createRequest);
        if (!qualify) {
            return created;
        }
        return qualify(request.companyId(), created.id());
    }

    @Transactional
    public LeadResponse qualify(UUID companyId, UUID id) {
        Lead lead = requireLead(companyId, id);
        CompanySettings settings = settingsOf(companyId);
        String prompt = promptCatalog.leadQualification(Map.of(
                "fullName", nullToEmpty(lead.getFullName()),
                "email", nullToEmpty(lead.getEmail()),
                "companyName", nullToEmpty(lead.getCompanyName()),
                "phone", nullToEmpty(lead.getPhone()),
                "source", nullToEmpty(lead.getSource()),
                "summary", nullToEmpty(lead.getSummary())));
        AIResponse aiResponse =
                aiGateway.generate(new AIRequest(companyId, "lead-qualification", prompt));
        if (!aiResponse.isSuccess()) {
            lead.setAiStatus(LeadAiStatus.AI_UNAVAILABLE.name());
            addEvent(lead, "QUALIFICATION_FAILED", Map.of("reason", LeadAiStatus.AI_UNAVAILABLE.name()));
            audit(
                    companyId,
                    WORKFLOW_QUALIFICATION,
                    "QUALIFIED",
                    lead.getId(),
                    AuditStatus.ERROR,
                    Map.of("aiStatus", LeadAiStatus.AI_UNAVAILABLE.name()));
            return toResponse(lead, settings, true);
        }
        try {
            LeadQualificationParser.Result result = qualificationParser.parse(aiResponse.text());
            applyQualification(lead, result, settings);
            addEvent(
                    lead,
                    "QUALIFIED",
                    Map.of(
                            "aiStatus", lead.getAiStatus(),
                            "score", lead.getScore(),
                            "confidenceScore", lead.getConfidenceScore()));
            audit(
                    companyId,
                    WORKFLOW_QUALIFICATION,
                    "QUALIFIED",
                    lead.getId(),
                    AuditStatus.SUCCESS,
                    Map.of("aiStatus", lead.getAiStatus(), "score", lead.getScore()));
            return toResponse(lead, settings, true);
        } catch (AiParsingException ex) {
            lead.setAiStatus(LeadAiStatus.AI_PARSING_ERROR.name());
            addEvent(lead, "QUALIFICATION_FAILED", Map.of("reason", LeadAiStatus.AI_PARSING_ERROR.name()));
            audit(
                    companyId,
                    WORKFLOW_QUALIFICATION,
                    "QUALIFIED",
                    lead.getId(),
                    AuditStatus.ERROR,
                    Map.of("aiStatus", LeadAiStatus.AI_PARSING_ERROR.name()));
            return toResponse(lead, settings, true);
        }
    }

    private void applyQualification(Lead lead, LeadQualificationParser.Result result, CompanySettings settings) {
        lead.setScore(result.score());
        lead.setSummary(result.summary());
        lead.setProbableNeed(result.probableNeed());
        lead.setUrgency(result.urgency());
        lead.setPotentialBudget(truncate(result.potentialBudget(), 64));
        lead.setRecommendedAction(result.recommendedAction());
        lead.setConfidenceScore(result.confidenceScore());
        BigDecimal threshold = settings.getLeadConfidenceThreshold() == null
                ? new BigDecimal("0.700")
                : settings.getLeadConfidenceThreshold();
        if (result.confidenceScore().compareTo(threshold) < 0) {
            lead.setAiStatus(LeadAiStatus.REVIEW_REQUIRED.name());
            return;
        }
        lead.setAiStatus(LeadAiStatus.COMPLETED.name());
        if (LeadStatus.NEW.name().equals(lead.getStatus())) {
            lead.setStatus(LeadStatus.QUALIFIED.name());
        }
    }

    private Lead requireLead(UUID companyId, UUID id) {
        return leadRepository.findByIdAndCompany_Id(id, companyId).orElseThrow(LeadException::notFound);
    }

    private CompanySettings settingsOf(UUID companyId) {
        return companySettingsRepository.findByCompanyId(companyId).orElseGet(() -> {
            CompanySettings defaults = new CompanySettings();
            defaults.setLeadScoreLowMax(30);
            defaults.setLeadScoreMediumMax(60);
            defaults.setLeadScoreHighMax(80);
            defaults.setLeadConfidenceThreshold(new BigDecimal("0.700"));
            return defaults;
        });
    }

    private LeadResponse toResponse(Lead lead, CompanySettings settings, boolean includeEvents) {
        LeadResponse base = leadMapper.toResponse(lead);
        List<LeadEventResponse> events = includeEvents
                ? leadEventRepository.findByLeadIdOrderByOccurredAtAsc(lead.getId()).stream()
                        .map(leadMapper::toEventResponse)
                        .toList()
                : null;
        return new LeadResponse(
                base.id(),
                lead.getCompany().getId(),
                base.source(),
                base.status(),
                base.email(),
                base.fullName(),
                base.companyName(),
                base.phone(),
                base.score(),
                LeadScoreBands.label(
                        lead.getScore(),
                        settings.getLeadScoreLowMax(),
                        settings.getLeadScoreMediumMax(),
                        settings.getLeadScoreHighMax()),
                base.summary(),
                base.probableNeed(),
                base.urgency(),
                base.potentialBudget(),
                base.recommendedAction(),
                base.aiStatus(),
                base.confidenceScore(),
                base.createdAt(),
                base.updatedAt(),
                events);
    }

    private void addEvent(Lead lead, String eventType, Map<String, Object> payload) {
        LeadEvent event = new LeadEvent();
        event.setCompany(lead.getCompany());
        event.setLead(lead);
        event.setEventType(eventType);
        event.setPayload(new LinkedHashMap<>(payload));
        event.setOccurredAt(Instant.now());
        leadEventRepository.save(event);
    }

    private void audit(
            UUID companyId, String workflow, String action, UUID entityId, AuditStatus status, Map<String, Object> metadata) {
        auditService.record(new AuditRecord(
                companyId, workflow, action, ENTITY_TYPE, entityId.toString(), status, metadata));
    }

    private static String normalizeUrgency(String urgency) {
        String normalized = blankToNull(urgency);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!List.of("LOW", "NORMAL", "HIGH", "URGENT").contains(upper)) {
            throw LeadException.invalidStatus();
        }
        return upper;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
