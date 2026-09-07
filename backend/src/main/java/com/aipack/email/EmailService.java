package com.aipack.email;

import com.aipack.ai.AIRequest;
import com.aipack.ai.AIResponse;
import com.aipack.ai.AiGateway;
import com.aipack.ai.AiParsingException;
import com.aipack.ai.PromptCatalog;
import com.aipack.audit.AuditRecord;
import com.aipack.audit.AuditService;
import com.aipack.audit.AuditStatus;
import com.aipack.config.DocumentProperties;
import com.aipack.config.EmailProperties;
import com.aipack.document.DocumentException;
import com.aipack.document.DocumentMimeDetector;
import com.aipack.email.dto.EmailAttachmentResponse;
import com.aipack.email.dto.EmailResponse;
import com.aipack.email.dto.WebhookEmailAttachmentRequest;
import com.aipack.email.dto.WebhookEmailRequest;
import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import com.aipack.identity.CompanySettings;
import com.aipack.identity.CompanySettingsRepository;
import com.aipack.storage.ObjectStorage;
import com.aipack.storage.StorageException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailService {

    static final String WORKFLOW_INGESTION = "email-ingestion";
    static final String WORKFLOW_ANALYSIS = "email-ai-analysis";
    static final String WORKFLOW_REPLY = "email-reply-generation";
    static final String ENTITY_TYPE = "EMAIL";

    private final EmailRepository emailRepository;
    private final EmailAnalysisRepository emailAnalysisRepository;
    private final EmailAttachmentRepository emailAttachmentRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final EmailMapper emailMapper;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final AiGateway aiGateway;
    private final PromptCatalog promptCatalog;
    private final EmailAnalysisParser analysisParser;
    private final OutboundMailSender outboundMailSender;
    private final EmailProperties emailProperties;
    private final DocumentProperties documentProperties;
    private final DocumentMimeDetector mimeDetector;
    private final ObjectStorage objectStorage;

    public EmailService(
            EmailRepository emailRepository,
            EmailAnalysisRepository emailAnalysisRepository,
            EmailAttachmentRepository emailAttachmentRepository,
            CompanyRepository companyRepository,
            CompanySettingsRepository companySettingsRepository,
            EmailMapper emailMapper,
            AuditService auditService,
            EntityManager entityManager,
            AiGateway aiGateway,
            PromptCatalog promptCatalog,
            EmailAnalysisParser analysisParser,
            OutboundMailSender outboundMailSender,
            EmailProperties emailProperties,
            DocumentProperties documentProperties,
            DocumentMimeDetector mimeDetector,
            ObjectStorage objectStorage) {
        this.emailRepository = emailRepository;
        this.emailAnalysisRepository = emailAnalysisRepository;
        this.emailAttachmentRepository = emailAttachmentRepository;
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.emailMapper = emailMapper;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.aiGateway = aiGateway;
        this.promptCatalog = promptCatalog;
        this.analysisParser = analysisParser;
        this.outboundMailSender = outboundMailSender;
        this.emailProperties = emailProperties;
        this.documentProperties = documentProperties;
        this.mimeDetector = mimeDetector;
        this.objectStorage = objectStorage;
    }

    @Transactional(readOnly = true)
    public Page<EmailResponse> list(
            UUID companyId, String status, String category, String priority, String approvalStatus, String q, Pageable pageable) {
        String statusFilter = blankToNull(status) == null ? null : EmailStatus.from(status).name();
        String categoryFilter = blankToNull(category) == null ? null : EmailCategory.from(category).name();
        String priorityFilter = blankToNull(priority) == null ? null : EmailPriority.from(priority).name();
        String approvalFilter =
                blankToNull(approvalStatus) == null ? null : EmailApprovalStatus.from(approvalStatus).name();
        String query = blankToNull(q);
        Specification<Email> spec = (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            Join<Email, EmailAnalysis> analysisJoin = null;
            if (categoryFilter != null || priorityFilter != null || approvalFilter != null) {
                analysisJoin = root.join("analysis", JoinType.INNER);
            }
            if (categoryFilter != null) {
                predicates.add(cb.equal(analysisJoin.get("category"), categoryFilter));
            }
            if (priorityFilter != null) {
                predicates.add(cb.equal(analysisJoin.get("priority"), priorityFilter));
            }
            if (approvalFilter != null) {
                predicates.add(cb.equal(analysisJoin.get("approvalStatus"), approvalFilter));
            }
            if (query != null) {
                String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("subject"), "")), like),
                        cb.like(cb.lower(root.get("fromAddress")), like),
                        cb.like(cb.lower(root.get("toAddress")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        CompanySettings settings = settingsOf(companyId);
        return emailRepository.findAll(spec, pageable).map(email -> toResponse(email, settings));
    }

    @Transactional(readOnly = true)
    public EmailResponse get(UUID companyId, UUID id) {
        return toResponse(requireEmail(companyId, id), settingsOf(companyId));
    }

    @Transactional
    public IngestResult ingestFromWebhook(WebhookEmailRequest request, boolean analyze) {
        if (!companyRepository.existsById(request.companyId())) {
            throw EmailException.companyNotFound();
        }
        String messageId = blankToNull(request.messageId());
        if (messageId != null) {
            var existing = emailRepository.findByCompany_IdAndMessageId(request.companyId(), messageId);
            if (existing.isPresent()) {
                Email email = existing.get();
                if (analyze && EmailStatus.RECEIVED.name().equals(email.getStatus())) {
                    return new IngestResult(analyze(request.companyId(), email.getId()), false);
                }
                return new IngestResult(toResponse(email, settingsOf(request.companyId())), false);
            }
        }
        Email email = new Email();
        email.setCompany(entityManager.getReference(Company.class, request.companyId()));
        email.setMessageId(messageId);
        email.setFromAddress(request.fromAddress().trim());
        email.setToAddress(request.toAddress().trim());
        email.setSubject(blankToNull(request.subject()));
        email.setBodyText(blankToNull(request.bodyText()));
        email.setReceivedAt(request.receivedAt() == null ? Instant.now() : request.receivedAt());
        email.setStatus(EmailStatus.RECEIVED.name());
        Email saved = emailRepository.save(email);
        storeAttachments(saved, request.attachments());
        audit(
                request.companyId(),
                WORKFLOW_INGESTION,
                "INGESTED",
                saved.getId(),
                AuditStatus.SUCCESS,
                Map.of(
                        "fromAddress",
                        saved.getFromAddress(),
                        "attachmentCount",
                        request.attachments() == null ? 0 : request.attachments().size()));
        if (!analyze) {
            return new IngestResult(toResponse(saved, settingsOf(request.companyId())), true);
        }
        return new IngestResult(analyze(request.companyId(), saved.getId()), true);
    }

    @Transactional(readOnly = true)
    public AttachmentContent loadAttachmentContent(UUID companyId, UUID emailId, UUID attachmentId) {
        requireEmail(companyId, emailId);
        EmailAttachment attachment = emailAttachmentRepository
                .findByIdAndEmail_IdAndCompany_Id(attachmentId, emailId, companyId)
                .orElseThrow(EmailException::attachmentNotFound);
        try {
            byte[] bytes = objectStorage.get(attachment.getStorageKey());
            return new AttachmentContent(
                    attachment.getOriginalFilename(), attachment.getContentType(), bytes);
        } catch (StorageException ex) {
            throw EmailException.storageFailed();
        }
    }

    @Transactional
    public EmailResponse analyze(UUID companyId, UUID id) {
        Email email = requireEmail(companyId, id);
        EmailAnalysis existing = emailAnalysisRepository.findByEmailId(email.getId()).orElse(null);
        if (existing != null && EmailApprovalStatus.EXECUTED.name().equals(existing.getApprovalStatus())) {
            throw EmailException.alreadySent();
        }
        CompanySettings settings = settingsOf(companyId);
        Map<String, String> variables = emailVariables(email);
        AIResponse classificationResponse =
                aiGateway.generate(new AIRequest(companyId, "email-classification", promptCatalog.emailClassification(variables)));
        if (!classificationResponse.isSuccess()) {
            return failAnalysis(email, settings, EmailAnalysisStatus.AI_UNAVAILABLE);
        }
        EmailAnalysisParser.Classification classification;
        try {
            classification = analysisParser.parseClassification(classificationResponse.text());
        } catch (AiParsingException ex) {
            return failAnalysis(email, settings, EmailAnalysisStatus.AI_PARSING_ERROR);
        }

        EmailAnalysisParser.Reply reply = null;
        boolean replyFailed = false;
        if (!EmailCategory.SPAM.name().equals(classification.category())) {
            Map<String, String> replyVariables = emailVariables(email);
            replyVariables.put("category", classification.category());
            replyVariables.put("priority", classification.priority());
            replyVariables.put("intent", nullToEmpty(classification.intent()));
            replyVariables.put("summary", nullToEmpty(classification.summary()));
            AIResponse replyResponse = aiGateway.generate(
                    new AIRequest(companyId, "email-response", promptCatalog.emailResponse(replyVariables)));
            if (!replyResponse.isSuccess()) {
                replyFailed = true;
            } else {
                try {
                    reply = analysisParser.parseReply(replyResponse.text());
                } catch (AiParsingException ex) {
                    replyFailed = true;
                }
            }
            if (reply == null) {
                // Garde une proposition utilisable pour la validation humaine (petits modèles).
                reply = new EmailAnalysisParser.Reply(
                        """
                        Bonjour,

                        Merci pour votre message. Nous avons bien reçu votre demande et revenons vers vous rapidement avec les éléments demandés.

                        Cordialement
                        """.stripIndent().trim(),
                        new BigDecimal("0.40"));
                replyFailed = true;
            }
        }

        EmailAnalysis analysis = existing == null ? new EmailAnalysis() : existing;
        analysis.setCompany(email.getCompany());
        analysis.setEmail(email);
        analysis.setCategory(classification.category());
        analysis.setPriority(classification.priority());
        analysis.setIntent(classification.intent());
        analysis.setSummary(classification.summary());
        analysis.setSuggestedReply(reply == null ? null : reply.suggestedReply());
        BigDecimal confidence = classification.confidenceScore();
        if (reply != null && reply.confidenceScore() != null && reply.confidenceScore().compareTo(confidence) < 0) {
            confidence = reply.confidenceScore();
        }
        analysis.setConfidenceScore(confidence);
        BigDecimal threshold = settings.getEmailConfidenceThreshold() == null
                ? new BigDecimal("0.700")
                : settings.getEmailConfidenceThreshold();
        boolean reviewRequired = confidence.compareTo(threshold) < 0 || replyFailed;
        if (reviewRequired) {
            analysis.setStatus(EmailAnalysisStatus.REVIEW_REQUIRED.name());
        } else {
            analysis.setStatus(EmailAnalysisStatus.COMPLETED.name());
        }
        if (EmailCategory.SPAM.name().equals(classification.category())) {
            analysis.setApprovalStatus(null);
        } else {
            analysis.setApprovalStatus(EmailApprovalStatus.PENDING_APPROVAL.name());
        }
        EmailAnalysis savedAnalysis = emailAnalysisRepository.save(analysis);
        email.setAnalysis(savedAnalysis);
        email.setStatus(EmailStatus.ANALYZED.name());
        audit(
                companyId,
                WORKFLOW_ANALYSIS,
                "ANALYZED",
                email.getId(),
                AuditStatus.SUCCESS,
                Map.of(
                        "category", savedAnalysis.getCategory(),
                        "priority", savedAnalysis.getPriority(),
                        "aiStatus", savedAnalysis.getStatus()));
        return toResponse(email, settings);
    }

    @Transactional
    public EmailResponse approve(UUID companyId, UUID id) {
        Email email = requireEmail(companyId, id);
        EmailAnalysis analysis = requirePendingReply(email);
        analysis.setApprovalStatus(EmailApprovalStatus.APPROVED.name());
        audit(
                companyId,
                WORKFLOW_REPLY,
                "APPROVED",
                email.getId(),
                AuditStatus.SUCCESS,
                Map.of("category", analysis.getCategory()));
        CompanySettings settings = settingsOf(companyId);
        if (autoSendEnabled(settings)) {
            return dispatchSend(email, analysis, settings, true);
        }
        return toResponse(email, settings);
    }

    @Transactional
    public EmailResponse reject(UUID companyId, UUID id) {
        Email email = requireEmail(companyId, id);
        EmailAnalysis analysis = requirePendingReply(email);
        analysis.setApprovalStatus(EmailApprovalStatus.REJECTED.name());
        audit(
                companyId,
                WORKFLOW_REPLY,
                "REJECTED",
                email.getId(),
                AuditStatus.SUCCESS,
                Map.of("category", analysis.getCategory()));
        return toResponse(email, settingsOf(companyId));
    }

    @Transactional
    public EmailResponse send(UUID companyId, UUID id) {
        Email email = requireEmail(companyId, id);
        EmailAnalysis analysis = email.getAnalysis();
        if (analysis == null || !EmailApprovalStatus.APPROVED.name().equals(analysis.getApprovalStatus())) {
            throw EmailException.sendNotAllowed();
        }
        return dispatchSend(email, analysis, settingsOf(companyId), false);
    }

    private EmailResponse dispatchSend(Email email, EmailAnalysis analysis, CompanySettings settings, boolean automatic) {
        if (EmailCategory.SPAM.name().equals(analysis.getCategory())
                || blankToNull(analysis.getSuggestedReply()) == null) {
            throw EmailException.sendNotAllowed();
        }
        if (EmailApprovalStatus.EXECUTED.name().equals(analysis.getApprovalStatus())) {
            throw EmailException.alreadySent();
        }
        try {
            outboundMailSender.send(new OutboundMail(
                    email.getToAddress(),
                    email.getFromAddress(),
                    replySubject(email.getSubject()),
                    analysis.getSuggestedReply()));
        } catch (EmailException ex) {
            audit(
                    email.getCompany().getId(),
                    WORKFLOW_REPLY,
                    "SENT",
                    email.getId(),
                    AuditStatus.ERROR,
                    Map.of("automatic", automatic));
            throw ex;
        } catch (MailException ex) {
            audit(
                    email.getCompany().getId(),
                    WORKFLOW_REPLY,
                    "SENT",
                    email.getId(),
                    AuditStatus.ERROR,
                    Map.of("automatic", automatic));
            throw EmailException.mailSendFailed();
        }
        analysis.setApprovalStatus(EmailApprovalStatus.EXECUTED.name());
        audit(
                email.getCompany().getId(),
                WORKFLOW_REPLY,
                "SENT",
                email.getId(),
                AuditStatus.SUCCESS,
                Map.of("automatic", automatic));
        return toResponse(email, settings);
    }

    private EmailResponse failAnalysis(Email email, CompanySettings settings, EmailAnalysisStatus aiStatus) {
        email.setStatus(EmailStatus.ERROR.name());
        audit(
                email.getCompany().getId(),
                WORKFLOW_ANALYSIS,
                "ANALYZED",
                email.getId(),
                AuditStatus.ERROR,
                Map.of("aiStatus", aiStatus.name()));
        return toResponse(email, settings);
    }

    private EmailAnalysis requirePendingReply(Email email) {
        EmailAnalysis analysis = email.getAnalysis();
        if (analysis == null
                || !EmailApprovalStatus.PENDING_APPROVAL.name().equals(analysis.getApprovalStatus())
                || EmailCategory.SPAM.name().equals(analysis.getCategory())
                || blankToNull(analysis.getSuggestedReply()) == null) {
            throw EmailException.approvalNotAllowed();
        }
        return analysis;
    }

    private Email requireEmail(UUID companyId, UUID id) {
        return emailRepository.findByIdAndCompany_Id(id, companyId).orElseThrow(EmailException::notFound);
    }

    private CompanySettings settingsOf(UUID companyId) {
        return companySettingsRepository.findByCompanyId(companyId).orElseGet(() -> {
            CompanySettings defaults = new CompanySettings();
            defaults.setAiGeneratedEmailAutoSend(false);
            defaults.setEmailConfidenceThreshold(new BigDecimal("0.700"));
            return defaults;
        });
    }

    private boolean autoSendEnabled(CompanySettings settings) {
        return emailProperties.autoSend() && settings.isAiGeneratedEmailAutoSend();
    }

    private EmailResponse toResponse(Email email, CompanySettings settings) {
        EmailResponse base = emailMapper.toResponse(email);
        List<EmailAttachmentResponse> attachments = emailAttachmentRepository
                .findByEmail_IdAndCompany_IdOrderByCreatedAtAsc(email.getId(), email.getCompany().getId())
                .stream()
                .map(a -> new EmailAttachmentResponse(
                        a.getId(),
                        a.getOriginalFilename(),
                        a.getContentType(),
                        a.getSizeBytes(),
                        a.getChecksumSha256()))
                .toList();
        return new EmailResponse(
                base.id(),
                email.getCompany().getId(),
                base.messageId(),
                base.fromAddress(),
                base.toAddress(),
                base.subject(),
                base.bodyText(),
                base.receivedAt(),
                base.status(),
                base.analysis(),
                attachments,
                autoSendEnabled(settings),
                base.createdAt(),
                base.updatedAt());
    }

    private void storeAttachments(Email email, List<WebhookEmailAttachmentRequest> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        if (attachments.size() > 10) {
            throw EmailException.tooManyAttachments();
        }
        UUID companyId = email.getCompany().getId();
        for (WebhookEmailAttachmentRequest item : attachments) {
            byte[] content;
            try {
                content = Base64.getDecoder().decode(item.contentBase64().trim());
            } catch (IllegalArgumentException ex) {
                throw EmailException.invalidAttachment();
            }
            if (content.length == 0) {
                throw EmailException.invalidAttachment();
            }
            if (content.length > documentProperties.maxSizeOrDefault()) {
                throw EmailException.attachmentTooLarge();
            }
            DocumentMimeDetector.DetectedFile detected;
            try {
                detected = mimeDetector.detect(content, item.filename());
            } catch (DocumentException ex) {
                if ("UNSUPPORTED_TYPE".equals(ex.getCode())) {
                    throw EmailException.unsupportedAttachmentType();
                }
                throw EmailException.invalidAttachment();
            }
            String checksum = sha256(content);
            String storageKey =
                    companyId + "/emails/" + email.getId() + "/" + checksum + "/" + detected.sanitizedFilename();
            try {
                objectStorage.put(storageKey, content, detected.contentType());
            } catch (StorageException ex) {
                throw EmailException.storageFailed();
            }
            EmailAttachment row = new EmailAttachment();
            row.setCompany(email.getCompany());
            row.setEmail(email);
            row.setOriginalFilename(
                    item.filename() == null || item.filename().isBlank()
                            ? detected.sanitizedFilename()
                            : item.filename().trim());
            row.setContentType(detected.contentType());
            row.setStorageKey(storageKey);
            row.setSizeBytes(content.length);
            row.setChecksumSha256(checksum);
            try {
                emailAttachmentRepository.save(row);
            } catch (RuntimeException ex) {
                try {
                    objectStorage.delete(storageKey);
                } catch (StorageException ignored) {
                    // best-effort cleanup
                }
                throw ex;
            }
        }
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, String> emailVariables(Email email) {
        return new LinkedHashMap<>(Map.of(
                "fromAddress", nullToEmpty(email.getFromAddress()),
                "toAddress", nullToEmpty(email.getToAddress()),
                "subject", nullToEmpty(email.getSubject()),
                "receivedAt", email.getReceivedAt() == null ? "" : email.getReceivedAt().toString(),
                "bodyText", nullToEmpty(email.getBodyText())));
    }

    private void audit(
            UUID companyId, String workflow, String action, UUID entityId, AuditStatus status, Map<String, Object> metadata) {
        auditService.record(new AuditRecord(
                companyId, workflow, action, ENTITY_TYPE, entityId.toString(), status, metadata));
    }

    static String replySubject(String subject) {
        String trimmed = subject == null ? "" : subject.trim();
        if (trimmed.isBlank()) {
            return "Re:";
        }
        if (trimmed.regionMatches(true, 0, "Re:", 0, 3)) {
            return trimmed;
        }
        return "Re: " + trimmed;
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

    public record IngestResult(EmailResponse email, boolean created) {}

    public record AttachmentContent(String filename, String contentType, byte[] bytes) {}
}
