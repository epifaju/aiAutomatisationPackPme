package com.aipack.report;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DailyReportSummaryParser {

    private final ObjectMapper objectMapper;

    public DailyReportSummaryParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiParsingException("Réponse IA vide");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            Payload payload = objectMapper.treeToValue(node, Payload.class);
            if (payload == null || payload.summary == null || payload.summary.isBlank()) {
                throw new AiParsingException("summary obligatoire");
            }
            return payload.summary.trim();
        } catch (AiParsingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiParsingException("JSON de rapport invalide", ex);
        }
    }

    static String extractJson(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Payload(String summary) {}
}
