package com.aipack.document;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parse tolérant : les petits modèles (ex. llama3.2:1b) renvoient souvent des dates FR,
 * des types approximatifs ou un JSON partiel. On normalise au lieu d'échouer systématiquement.
 */
@Component
public class DocumentExtractionParser {

    private static final Set<String> TYPES =
            Set.of("FACTURE", "DEVIS", "BON_COMMANDE", "CONTRAT", "COURRIER", "AUTRE");
    private static final Pattern FR_DATE = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ObjectMapper objectMapper;

    public DocumentExtractionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedExtraction parse(String raw) {
        JsonNode root = readTree(raw);
        String documentType = normalizeType(text(root, "documentType"));
        String summary = text(root, "summary");
        if (summary == null || summary.isBlank()) {
            summary = "Document analysé ; détails partiels extraits par IA.";
        }

        BigDecimal confidence = decimal(root.get("confidenceScore"));
        if (confidence == null) {
            confidence = new BigDecimal("0.50");
        }
        confidence = clamp01(confidence);

        // Pénaliser si champs critiques manquants / dates non ISO d'origine
        if (text(root, "summary") == null || text(root, "summary").isBlank()) {
            confidence = confidence.min(new BigDecimal("0.55"));
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("documentType", documentType);
        json.put("supplier", blankToNull(text(root, "supplier")));
        json.put("customer", blankToNull(text(root, "customer")));
        json.put("invoiceNumber", blankToNull(text(root, "invoiceNumber")));
        json.put("invoiceDate", parseDateSoft(text(root, "invoiceDate")));
        json.put("dueDate", parseDateSoft(text(root, "dueDate")));
        json.put("amountExcludingTax", decimal(root.get("amountExcludingTax")));
        json.put("vat", decimal(root.get("vat")));
        json.put("amountIncludingTax", decimal(root.get("amountIncludingTax")));
        json.put("currency", currencySoft(text(root, "currency")));
        json.put("summary", summary.trim());
        json.put("confidenceScore", confidence);
        return new ParsedExtraction(documentType, json, confidence);
    }

    private JsonNode readTree(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AiParsingException("Réponse IA vide");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            if (node == null || node.isNull() || !node.isObject()) {
                throw new AiParsingException("JSON d'extraction vide");
            }
            return node;
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

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) {
            return "AUTRE";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        // aliases fréquents des petits modèles
        if (normalized.contains("FACTUR") || normalized.equals("INVOICE")) {
            return "FACTURE";
        }
        if (normalized.contains("DEVIS") || normalized.equals("QUOTE") || normalized.equals("ESTIMATE")) {
            return "DEVIS";
        }
        if (normalized.contains("COMMANDE") || normalized.equals("PO") || normalized.contains("ORDER")) {
            return "BON_COMMANDE";
        }
        if (normalized.contains("CONTRAT") || normalized.equals("CONTRACT")) {
            return "CONTRAT";
        }
        if (normalized.contains("COURRIER") || normalized.equals("LETTER") || normalized.equals("MAIL")) {
            return "COURRIER";
        }
        if (TYPES.contains(normalized)) {
            return normalized;
        }
        return "AUTRE";
    }

    private static String parseDateSoft(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null || trimmed.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed, ISO).toString();
        } catch (DateTimeParseException ignored) {
            // continue
        }
        Matcher m = FR_DATE.matcher(trimmed);
        if (m.matches()) {
            int d = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            try {
                return LocalDate.of(y, mo, d).toString();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String currencySoft(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return "EUR";
        }
        String normalized = trimmed.toUpperCase(Locale.ROOT).replace("€", "EUR");
        if (normalized.contains("EURO")) {
            return "EUR";
        }
        if (normalized.length() == 3) {
            return normalized;
        }
        return "EUR";
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText(null);
        if (text == null || text.isBlank() || text.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return new BigDecimal(text.trim().replace(',', '.').replace(" ", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal clamp01(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            // modèles qui renvoient 0-100
            if (value.compareTo(new BigDecimal("100")) <= 0) {
                return value.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            }
            return BigDecimal.ONE;
        }
        return value;
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) {
            return null;
        }
        return value.trim();
    }

    public record ParsedExtraction(String documentType, Map<String, Object> json, BigDecimal confidenceScore) {}
}
