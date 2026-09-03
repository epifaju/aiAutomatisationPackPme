package com.aipack.email;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EmailAnalysisParser {

    private static final Set<String> CATEGORIES = Set.of(
            "CLIENT", "PROSPECT", "FACTURE", "FOURNISSEUR", "SUPPORT", "ADMINISTRATIF", "SPAM", "AUTRE");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    private final ObjectMapper objectMapper;

    public EmailAnalysisParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Classification parseClassification(String raw) {
        ClassificationPayload payload = read(raw, ClassificationPayload.class, "classification");
        if (payload.summary == null || payload.summary.isBlank()) {
            throw new AiParsingException("summary obligatoire");
        }
        String category = normalizeEnum(payload.category, CATEGORIES, "category");
        String priority = normalizeEnum(payload.priority, PRIORITIES, "priority");
        return new Classification(
                category,
                priority,
                truncate(blankToNull(payload.intent), 128),
                payload.summary.trim(),
                requireConfidence(payload.confidenceScore));
    }

    public Reply parseReply(String raw) {
        ReplyPayload payload = read(raw, ReplyPayload.class, "réponse");
        if (payload.suggestedReply == null || payload.suggestedReply.isBlank()) {
            throw new AiParsingException("suggestedReply obligatoire");
        }
        return new Reply(payload.suggestedReply.trim(), optionalConfidence(payload.confidenceScore));
    }

    private <T> T read(String raw, Class<T> type, String label) {
        if (raw == null || raw.isBlank()) {
            throw new AiParsingException("Réponse IA vide");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            T payload = objectMapper.treeToValue(node, type);
            if (payload == null) {
                throw new AiParsingException("JSON de " + label + " vide");
            }
            return payload;
        } catch (AiParsingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiParsingException("JSON de " + label + " invalide", ex);
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

    private static BigDecimal optionalConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return null;
        }
        return requireConfidence(confidence);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record Classification(
            String category, String priority, String intent, String summary, BigDecimal confidenceScore) {}

    public record Reply(String suggestedReply, BigDecimal confidenceScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClassificationPayload(
            String category, String priority, String intent, String summary, BigDecimal confidenceScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReplyPayload(String suggestedReply, BigDecimal confidenceScore) {}
}
