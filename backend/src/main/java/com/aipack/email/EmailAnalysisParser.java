package com.aipack.email;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Parse tolérant pour petits modèles locaux (catégories approximatives, confidence 0-100, JSON partiel).
 */
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
        JsonNode root = readTree(raw, "classification");
        String category = normalizeCategory(text(root, "category"));
        String priority = normalizePriority(text(root, "priority"));
        String summary = text(root, "summary");
        if (summary == null || summary.isBlank()) {
            summary = "Message analysé automatiquement.";
        }
        BigDecimal confidence = clamp01(decimal(root.get("confidenceScore")), new BigDecimal("0.55"));
        // SPAM peu confiant → AUTRE (évite de bloquer la file d'approbation à tort)
        if ("SPAM".equals(category) && confidence.compareTo(new BigDecimal("0.80")) < 0) {
            category = "AUTRE";
            confidence = confidence.min(new BigDecimal("0.50"));
        }
        return new Classification(
                category,
                priority,
                truncate(blankToNull(text(root, "intent")), 128),
                summary.trim(),
                confidence);
    }

    public Reply parseReply(String raw) {
        JsonNode root = readTree(raw, "réponse");
        String suggested = text(root, "suggestedReply");
        if (suggested == null || suggested.isBlank()) {
            suggested = text(root, "reply");
        }
        if (suggested == null || suggested.isBlank()) {
            throw new AiParsingException("suggestedReply obligatoire");
        }
        return new Reply(suggested.trim(), clamp01(decimal(root.get("confidenceScore")), null));
    }

    private JsonNode readTree(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new AiParsingException("Réponse IA vide");
        }
        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            if (node == null || node.isNull() || !node.isObject()) {
                throw new AiParsingException("JSON de " + label + " vide");
            }
            return node;
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

    private static String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return "AUTRE";
        }
        String n = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (n.contains("PROSPECT") || n.contains("LEAD") || n.contains("DEVIS") || n.contains("QUOTE")) {
            return "PROSPECT";
        }
        if (n.contains("CLIENT") || n.contains("CUSTOMER")) {
            return "CLIENT";
        }
        if (n.contains("FACTUR") || n.contains("INVOICE")) {
            return "FACTURE";
        }
        if (n.contains("FOURNIS") || n.contains("VENDOR") || n.contains("SUPPLIER")) {
            return "FOURNISSEUR";
        }
        if (n.contains("SUPPORT") || n.contains("AIDE") || n.contains("HELP")) {
            return "SUPPORT";
        }
        if (n.contains("ADMIN")) {
            return "ADMINISTRATIF";
        }
        if (n.contains("SPAM") || n.contains("PUB") || n.contains("PROMO")) {
            return "SPAM";
        }
        if (CATEGORIES.contains(n)) {
            return n;
        }
        return "AUTRE";
    }

    private static String normalizePriority(String value) {
        if (value == null || value.isBlank()) {
            return "NORMAL";
        }
        String n = value.trim().toUpperCase(Locale.ROOT);
        if (n.contains("URGENT") || n.equals("CRITIQUE")) {
            return "URGENT";
        }
        if (n.contains("HIGH") || n.contains("HAUTE") || n.contains("ELEVE")) {
            return "HIGH";
        }
        if (n.contains("LOW") || n.contains("BASSE") || n.contains("FAIBLE")) {
            return "LOW";
        }
        if (PRIORITIES.contains(n)) {
            return n;
        }
        return "NORMAL";
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

    private static BigDecimal clamp01(BigDecimal value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
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

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record Classification(
            String category, String priority, String intent, String summary, BigDecimal confidenceScore) {}

    public record Reply(String suggestedReply, BigDecimal confidenceScore) {}
}
