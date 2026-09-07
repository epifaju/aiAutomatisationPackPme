package com.aipack.document;

import com.aipack.ai.AIRequest;
import com.aipack.ai.AIResponse;
import com.aipack.ai.AiGateway;
import com.aipack.ai.AiParsingException;
import com.aipack.ai.PromptCatalog;
import com.aipack.audit.AuditRecord;
import com.aipack.audit.AuditService;
import com.aipack.audit.AuditStatus;
import com.aipack.config.DocumentProperties;
import com.aipack.document.dto.ApproveDocumentRequest;
import com.aipack.document.dto.DocumentResponse;
import com.aipack.document.dto.WebhookDocumentRequest;
import com.aipack.identity.Company;
import com.aipack.identity.CompanyRepository;
import com.aipack.identity.CompanySettings;
import com.aipack.identity.CompanySettingsRepository;
import com.aipack.storage.ObjectStorage;
import com.aipack.storage.StorageException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    static final String WORKFLOW_INGESTION = "document-ingestion";
    static final String WORKFLOW_EXTRACTION = "document-ai-extraction";
    static final String ENTITY_TYPE = "DOCUMENT";

    private final DocumentRepository documentRepository;
    private final DocumentExtractionRepository extractionRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final DocumentMapper documentMapper;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final ObjectStorage objectStorage;
    private final DocumentMimeDetector mimeDetector;
    private final TikaTextExtractor textExtractor;
    private final AiGateway aiGateway;
    private final PromptCatalog promptCatalog;
    private final DocumentExtractionParser extractionParser;
    private final DocumentHeuristicExtractor heuristicExtractor;
    private final DocumentProperties documentProperties;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentExtractionRepository extractionRepository,
            CompanyRepository companyRepository,
            CompanySettingsRepository companySettingsRepository,
            DocumentMapper documentMapper,
            AuditService auditService,
            EntityManager entityManager,
            ObjectStorage objectStorage,
            DocumentMimeDetector mimeDetector,
            TikaTextExtractor textExtractor,
            AiGateway aiGateway,
            PromptCatalog promptCatalog,
            DocumentExtractionParser extractionParser,
            DocumentHeuristicExtractor heuristicExtractor,
            DocumentProperties documentProperties) {
        this.documentRepository = documentRepository;
        this.extractionRepository = extractionRepository;
        this.companyRepository = companyRepository;
        this.companySettingsRepository = companySettingsRepository;
        this.documentMapper = documentMapper;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.objectStorage = objectStorage;
        this.mimeDetector = mimeDetector;
        this.textExtractor = textExtractor;
        this.aiGateway = aiGateway;
        this.promptCatalog = promptCatalog;
        this.extractionParser = extractionParser;
        this.heuristicExtractor = heuristicExtractor;
        this.documentProperties = documentProperties;
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> list(UUID companyId, String status, String documentType, String q, Pageable pageable) {
        String statusFilter = blankToNull(status) == null ? null : DocumentStatus.from(status).name();
        String typeFilter = blankToNull(documentType) == null ? null : DocumentType.from(documentType).name();
        String query = blankToNull(q);
        Specification<Document> spec = (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (typeFilter != null) {
                predicates.add(cb.equal(root.get("documentType"), typeFilter));
            }
            if (query != null) {
                String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("originalFilename")), like));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return documentRepository.findAll(spec, pageable).map(documentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID companyId, UUID id) {
        return documentMapper.toResponse(requireDocument(companyId, id));
    }

    @Transactional
    public IngestResult upload(UUID companyId, MultipartFile file, String documentType, boolean process) {
        if (file == null || file.isEmpty()) {
            throw DocumentException.missingFile();
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception ex) {
            throw DocumentException.missingFile();
        }
        return ingest(companyId, file.getOriginalFilename(), content, documentType, process);
    }

    @Transactional
    public IngestResult ingestFromWebhook(WebhookDocumentRequest request, boolean process) {
        if (!companyRepository.existsById(request.companyId())) {
            throw DocumentException.companyNotFound();
        }
        if (request.documentId() != null) {
            DocumentResponse processed = process
                    ? process(request.companyId(), request.documentId())
                    : get(request.companyId(), request.documentId());
            return new IngestResult(processed, false);
        }
        if (blankToNull(request.contentBase64()) == null || blankToNull(request.originalFilename()) == null) {
            throw DocumentException.webhookPayload();
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(request.contentBase64().trim());
        } catch (IllegalArgumentException ex) {
            throw DocumentException.webhookPayload();
        }
        return ingest(request.companyId(), request.originalFilename(), content, request.documentType(), process);
    }

    @Transactional
    public DocumentResponse process(UUID companyId, UUID id) {
        Document document = requireDocument(companyId, id);
        if (DocumentStatus.PROCESSING.name().equals(document.getStatus())) {
            throw DocumentException.processingNotAllowed();
        }
        document.setStatus(DocumentStatus.PROCESSING.name());
        documentRepository.saveAndFlush(document);

        byte[] content;
        try {
            content = objectStorage.get(document.getStorageKey());
        } catch (StorageException ex) {
            return failProcessing(document, Map.of("reason", "STORAGE_ERROR"), AuditStatus.ERROR);
        }

        String extractedText;
        try {
            extractedText = textExtractor.extract(content, document.getOriginalFilename());
        } catch (DocumentException ex) {
            return failProcessing(document, Map.of("reason", "TIKA_ERROR"), AuditStatus.ERROR);
        }

        if (extractedText.isBlank()) {
            return persistExtraction(
                    document,
                    settingsOf(companyId),
                    extractedText,
                    Map.of("reason", "NO_TEXT_EXTRACTED"),
                    BigDecimal.ZERO,
                    DocumentExtractionStatus.REVIEW_REQUIRED,
                    document.getDocumentType());
        }

        CompanySettings settings = settingsOf(companyId);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("originalFilename", nullToEmpty(document.getOriginalFilename()));
        variables.put("contentType", nullToEmpty(document.getContentType()));
        variables.put("declaredType", nullToEmpty(document.getDocumentType()));
        variables.put("extractedText", TikaTextExtractor.forPrompt(extractedText));
        AIResponse aiResponse =
                aiGateway.generate(new AIRequest(companyId, "document-extraction", promptCatalog.documentExtraction(variables)));
        if (!aiResponse.isSuccess()) {
            DocumentExtractionParser.ParsedExtraction fallback =
                    heuristicExtractor.tryExtract(extractedText, document.getDocumentType());
            if (fallback != null) {
                return persistExtraction(
                        document,
                        settings,
                        extractedText,
                        fallback.json(),
                        fallback.confidenceScore(),
                        DocumentExtractionStatus.REVIEW_REQUIRED,
                        fallback.documentType());
            }
            return persistExtraction(
                    document,
                    settings,
                    extractedText,
                    Map.of("reason", "AI_UNAVAILABLE"),
                    null,
                    DocumentExtractionStatus.AI_UNAVAILABLE,
                    document.getDocumentType());
        }
        DocumentExtractionParser.ParsedExtraction parsed;
        try {
            parsed = extractionParser.parse(aiResponse.text());
        } catch (AiParsingException ex) {
            parsed = heuristicExtractor.tryExtract(extractedText, document.getDocumentType());
            if (parsed == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("reason", "AI_PARSING_ERROR");
                err.put("message", ex.getMessage());
                String raw = aiResponse.text();
                if (raw != null && !raw.isBlank()) {
                    err.put("rawPreview", raw.length() > 500 ? raw.substring(0, 500) : raw);
                }
                return persistExtraction(
                        document,
                        settings,
                        extractedText,
                        err,
                        null,
                        DocumentExtractionStatus.AI_PARSING_ERROR,
                        document.getDocumentType());
            }
            // Repli déterministe → toujours revue humaine
            return persistExtraction(
                    document,
                    settings,
                    extractedText,
                    parsed.json(),
                    parsed.confidenceScore(),
                    DocumentExtractionStatus.REVIEW_REQUIRED,
                    parsed.documentType());
        }
        return persistExtraction(
                document,
                settings,
                extractedText,
                parsed.json(),
                parsed.confidenceScore(),
                DocumentExtractionStatus.COMPLETED,
                parsed.documentType());
    }

    @Transactional
    public DocumentResponse approve(UUID companyId, UUID id, ApproveDocumentRequest request) {
        Document document = requireDocument(companyId, id);
        DocumentExtraction extraction = requireReview(document);
        if (request != null && request.documentType() != null && !request.documentType().isBlank()) {
            document.setDocumentType(DocumentType.from(request.documentType()).name());
        }
        if (request != null && request.extractedJson() != null && !request.extractedJson().isEmpty()) {
            extraction.setExtractedJson(new LinkedHashMap<>(request.extractedJson()));
        }
        extraction.setStatus(DocumentExtractionStatus.COMPLETED.name());
        document.setStatus(DocumentStatus.EXTRACTED.name());
        audit(
                companyId,
                WORKFLOW_EXTRACTION,
                "APPROVED",
                document.getId(),
                AuditStatus.SUCCESS,
                Map.of("documentType", document.getDocumentType()));
        return documentMapper.toResponse(document);
    }

    @Transactional
    public DocumentResponse reject(UUID companyId, UUID id) {
        Document document = requireDocument(companyId, id);
        DocumentExtraction extraction = requireReview(document);
        extraction.setStatus(DocumentExtractionStatus.REVIEW_REQUIRED.name());
        document.setStatus(DocumentStatus.ERROR.name());
        audit(
                companyId,
                WORKFLOW_EXTRACTION,
                "REJECTED",
                document.getId(),
                AuditStatus.SUCCESS,
                Map.of("documentType", document.getDocumentType()));
        return documentMapper.toResponse(document);
    }

    private IngestResult ingest(
            UUID companyId, String originalFilename, byte[] content, String declaredType, boolean process) {
        if (!companyRepository.existsById(companyId)) {
            throw DocumentException.companyNotFound();
        }
        if (content.length > documentProperties.maxSizeOrDefault()) {
            throw DocumentException.fileTooLarge();
        }
        DocumentMimeDetector.DetectedFile detected = mimeDetector.detect(content, originalFilename);
        String checksum = sha256(content);
        var existing = documentRepository.findByCompany_IdAndChecksumSha256(companyId, checksum);
        if (existing.isPresent()) {
            Document document = existing.get();
            if (process && DocumentStatus.UPLOADED.name().equals(document.getStatus())) {
                return new IngestResult(process(companyId, document.getId()), false);
            }
            return new IngestResult(documentMapper.toResponse(document), false);
        }
        String storageKey = companyId + "/documents/" + checksum + "/" + detected.sanitizedFilename();
        try {
            objectStorage.put(storageKey, content, detected.contentType());
        } catch (StorageException ex) {
            throw DocumentException.storageFailed();
        }
        Document document = new Document();
        document.setCompany(entityManager.getReference(Company.class, companyId));
        document.setOriginalFilename(blankToNull(originalFilename) == null ? detected.sanitizedFilename() : originalFilename.trim());
        document.setContentType(detected.contentType());
        document.setStorageKey(storageKey);
        document.setSizeBytes(content.length);
        document.setDocumentType(
                blankToNull(declaredType) == null ? DocumentType.AUTRE.name() : DocumentType.from(declaredType).name());
        document.setStatus(DocumentStatus.UPLOADED.name());
        document.setChecksumSha256(checksum);
        Document saved;
        try {
            saved = documentRepository.save(document);
        } catch (RuntimeException ex) {
            try {
                objectStorage.delete(storageKey);
            } catch (StorageException ignored) {
                // best-effort cleanup
            }
            throw ex;
        }
        audit(
                companyId,
                WORKFLOW_INGESTION,
                "UPLOADED",
                saved.getId(),
                AuditStatus.SUCCESS,
                Map.of("contentType", saved.getContentType(), "sizeBytes", saved.getSizeBytes()));
        if (!process) {
            return new IngestResult(documentMapper.toResponse(saved), true);
        }
        return new IngestResult(process(companyId, saved.getId()), true);
    }

    private DocumentResponse persistExtraction(
            Document document,
            CompanySettings settings,
            String extractedText,
            Map<String, Object> json,
            BigDecimal confidence,
            DocumentExtractionStatus parsedStatus,
            String documentType) {
        DocumentExtraction extraction =
                extractionRepository.findByDocumentId(document.getId()).orElseGet(DocumentExtraction::new);
        extraction.setCompany(document.getCompany());
        extraction.setDocument(document);
        extraction.setExtractedJson(new LinkedHashMap<>(json));
        extraction.setExtractedText(extractedText);
        extraction.setConfidenceScore(confidence);

        DocumentExtractionStatus extractionStatus = parsedStatus;
        String documentStatus;
        if (parsedStatus == DocumentExtractionStatus.AI_UNAVAILABLE
                || parsedStatus == DocumentExtractionStatus.AI_PARSING_ERROR) {
            documentStatus = DocumentStatus.ERROR.name();
        } else if (parsedStatus == DocumentExtractionStatus.REVIEW_REQUIRED
                || confidence == null
                || confidence.compareTo(threshold(settings)) < 0) {
            extractionStatus = DocumentExtractionStatus.REVIEW_REQUIRED;
            documentStatus = DocumentStatus.REVIEW_REQUIRED.name();
        } else {
            extractionStatus = DocumentExtractionStatus.COMPLETED;
            documentStatus = DocumentStatus.EXTRACTED.name();
        }
        if (parsedStatus == DocumentExtractionStatus.COMPLETED && documentType != null) {
            document.setDocumentType(documentType);
        }
        extraction.setStatus(extractionStatus.name());
        DocumentExtraction saved = extractionRepository.save(extraction);
        document.setExtraction(saved);
        document.setStatus(documentStatus);
        AuditStatus auditStatus = DocumentStatus.ERROR.name().equals(documentStatus) ? AuditStatus.ERROR : AuditStatus.SUCCESS;
        audit(
                document.getCompany().getId(),
                WORKFLOW_EXTRACTION,
                "EXTRACTED",
                document.getId(),
                auditStatus,
                Map.of("aiStatus", extractionStatus.name(), "documentStatus", documentStatus));
        return documentMapper.toResponse(document);
    }

    private DocumentResponse failProcessing(Document document, Map<String, Object> metadata, AuditStatus status) {
        document.setStatus(DocumentStatus.ERROR.name());
        audit(document.getCompany().getId(), WORKFLOW_EXTRACTION, "EXTRACTED", document.getId(), status, metadata);
        return documentMapper.toResponse(document);
    }

    private DocumentExtraction requireReview(Document document) {
        DocumentExtraction extraction = document.getExtraction();
        if (extraction == null
                || !DocumentStatus.REVIEW_REQUIRED.name().equals(document.getStatus())
                || !DocumentExtractionStatus.REVIEW_REQUIRED.name().equals(extraction.getStatus())) {
            throw DocumentException.reviewNotAllowed();
        }
        return extraction;
    }

    private Document requireDocument(UUID companyId, UUID id) {
        return documentRepository.findByIdAndCompany_Id(id, companyId).orElseThrow(DocumentException::notFound);
    }

    private CompanySettings settingsOf(UUID companyId) {
        return companySettingsRepository.findByCompanyId(companyId).orElseGet(() -> {
            CompanySettings defaults = new CompanySettings();
            defaults.setDocumentConfidenceThreshold(new BigDecimal("0.700"));
            return defaults;
        });
    }

    private BigDecimal threshold(CompanySettings settings) {
        return settings.getDocumentConfidenceThreshold() == null
                ? new BigDecimal("0.700")
                : settings.getDocumentConfidenceThreshold();
    }

    private void audit(
            UUID companyId, String workflow, String action, UUID entityId, AuditStatus status, Map<String, Object> metadata) {
        auditService.record(new AuditRecord(
                companyId, workflow, action, ENTITY_TYPE, entityId.toString(), status, metadata));
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
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

    public record IngestResult(DocumentResponse document, boolean created) {}
}
