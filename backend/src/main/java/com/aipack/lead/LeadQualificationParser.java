package com.aipack.lead;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LeadQualificationParser {

    private static final Set<String> URGENCIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    private final ObjectMapper objectMapper;

    public LeadQualificationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Result parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiParsingException("Réponse IA vide");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            Payload payload = objectMapper.treeToValue(node, Payload.class);
            return validate(payload);
        } catch (AiParsingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiParsingException("JSON de qualification invalide", ex);
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

    private static Result validate(Payload payload) {
        if (payload == null) {
            throw new AiParsingException("JSON de qualification vide");
        }
        if (payload.score < 0 || payload.score > 100) {
            throw new AiParsingException("score hors intervalle 0-100");
        }
        if (payload.summary == null || payload.summary.isBlank()) {
            throw new AiParsingException("summary obligatoire");
        }
        String urgency = payload.urgency == null ? "" : payload.urgency.trim().toUpperCase(Locale.ROOT);
        if (!URGENCIES.contains(urgency)) {
            throw new AiParsingException("urgency invalide");
        }
        BigDecimal confidence = payload.confidenceScore == null ? BigDecimal.ZERO : payload.confidenceScore;
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new AiParsingException("confidenceScore hors intervalle 0-1");
        }
        return new Result(
                payload.score,
                payload.summary.trim(),
                blankToNull(payload.probableNeed),
                urgency,
                blankToNull(payload.potentialBudget),
                blankToNull(payload.recommendedAction),
                confidence);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record Result(
            int score,
            String summary,
            String probableNeed,
            String urgency,
            String potentialBudget,
            String recommendedAction,
            BigDecimal confidenceScore) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Payload(
            int score,
            String summary,
            String probableNeed,
            String urgency,
            String potentialBudget,
            BigDecimal confidenceScore,
            String recommendedAction) {}
}
