package com.aipack.document;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DocumentExtractionParser {

    private static final Set<String> TYPES = Set.of("FACTURE", "DEVIS", "BON_COMMANDE", "CONTRAT", "COURRIER", "AUTRE");

    private final ObjectMapper objectMapper;

    public DocumentExtractionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedExtraction parse(String raw) {
        Payload payload = read(raw);
        String documentType = normalizeEnum(payload.documentType, TYPES, "documentType");
        if (payload.summary == null || payload.summary.isBlank()) {
            throw new AiParsingException("summary obligatoire");
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("documentType", documentType);
        json.put("supplier", blankToNull(payload.supplier));
        json.put("customer", blankToNull(payload.customer));
        json.put("invoiceNumber", blankToNull(payload.invoiceNumber));
        json.put("invoiceDate", parseDate(payload.invoiceDate));
        json.put("dueDate", parseDate(payload.dueDate));
        json.put("amountExcludingTax", payload.amountExcludingTax);
        json.put("vat", payload.vat);
        json.put("amountIncludingTax", payload.amountIncludingTax);
        json.put("currency", currency(payload.currency));
        json.put("summary", payload.summary.trim());
        json.put("confidenceScore", requireConfidence(payload.confidenceScore));
        return new ParsedExtraction(documentType, json, requireConfidence(payload.confidenceScore));
    }

    private Payload read(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiParsingException("Réponse IA vide");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            Payload payload = objectMapper.treeToValue(node, Payload.class);
            if (payload == null) {
                throw new AiParsingException("JSON d'extraction vide");
            }
            return payload;
        } catch (AiParsingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiParsingException("JSON d'extraction invalide", ex);
        }
    }

    static String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private static String normalizeEnum(String value, Set<String> allowed, String field) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new AiParsingException(field + " invalide");
        }
        return normalized;
    }

    private static BigDecimal requireConfidence(BigDecimal confidence) {
        BigDecimal value = confidence == null ? BigDecimal.ZERO : confidence;
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new AiParsingException("confidenceScore hors intervalle 0-1");
        }
        return value;
    }

    private static String parseDate(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed).toString();
        } catch (DateTimeParseException ex) {
            throw new AiParsingException("date invalide : " + trimmed);
        }
    }

    private static String currency(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return "EUR";
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new AiParsingException("currency invalide");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ParsedExtraction(String documentType, Map<String, Object> json, BigDecimal confidenceScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Payload(
            String documentType,
            String supplier,
            String customer,
            String invoiceNumber,
            String invoiceDate,
            String dueDate,
            BigDecimal amountExcludingTax,
            BigDecimal vat,
            BigDecimal amountIncludingTax,
            String currency,
            String summary,
            BigDecimal confidenceScore) {}
}
