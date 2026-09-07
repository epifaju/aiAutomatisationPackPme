package com.aipack.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Repli déterministe quand l'IA renvoie un JSON illisible.
 * Couvre surtout les factures texte (HT/TVA/TTC, dates, numéro).
 */
@Component
public class DocumentHeuristicExtractor {

    private static final Pattern INVOICE_NO = Pattern.compile(
            "(?i)(?:facture|invoice|n[°o]|num(?:ero|éro)?)\\s*[:#]?\\s*([A-Z0-9][A-Z0-9._\\-/]{2,})");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(20\\d{2}-\\d{2}-\\d{2})\\b");
    private static final Pattern FR_DATE = Pattern.compile("\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](20\\d{2})\\b");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)\\b(HT|TVA|TTC|total)\\s*[:]?\\s*([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:EUR|€)?");
    private static final Pattern SUPPLIER = Pattern.compile("(?i)(?:fournisseur|emetteur|émetteur)\\s*:\\s*(.+)");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    public DocumentExtractionParser.ParsedExtraction tryExtract(String text, String declaredType) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replace('\u00a0', ' ').trim();
        String type = declaredType == null || declaredType.isBlank() ? "AUTRE" : declaredType.trim().toUpperCase(Locale.ROOT);
        if (type.contains("FACT") || normalized.toUpperCase(Locale.ROOT).contains("FACTURE")) {
            type = "FACTURE";
        }

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("documentType", type);
        json.put("supplier", firstGroup(SUPPLIER, normalized));
        json.put("customer", null);
        json.put("invoiceNumber", sanitizeInvoiceNo(firstGroup(INVOICE_NO, normalized)));
        String invoiceDate = firstIsoDate(normalized);
        String dueDate = secondIsoDate(normalized);
        json.put("invoiceDate", invoiceDate);
        json.put("dueDate", dueDate);

        BigDecimal ht = amountFor(normalized, "HT");
        BigDecimal vat = amountFor(normalized, "TVA");
        BigDecimal ttc = amountFor(normalized, "TTC");
        if (ttc == null) {
            ttc = amountFor(normalized, "TOTAL");
        }
        json.put("amountExcludingTax", ht);
        json.put("vat", vat);
        json.put("amountIncludingTax", ttc);
        json.put("currency", "EUR");

        boolean useful = json.get("invoiceNumber") != null
                || invoiceDate != null
                || ht != null
                || ttc != null
                || json.get("supplier") != null;
        if (!useful) {
            return null;
        }

        String summary = "Extraction heuristique (repli IA) : document " + type.toLowerCase(Locale.ROOT) + ".";
        json.put("summary", summary);
        BigDecimal confidence = new BigDecimal("0.45");
        json.put("confidenceScore", confidence);
        return new DocumentExtractionParser.ParsedExtraction(type, json, confidence);
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return null;
        }
        String value = m.group(1).trim();
        return value.isBlank() ? null : value;
    }

    private static String sanitizeInvoiceNo(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)^(facture|invoice)\\s*", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String firstIsoDate(String text) {
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find()) {
            return iso.group(1);
        }
        Matcher fr = FR_DATE.matcher(text);
        if (fr.find()) {
            return toIso(fr.group(1), fr.group(2), fr.group(3));
        }
        return null;
    }

    private static String secondIsoDate(String text) {
        Matcher iso = ISO_DATE.matcher(text);
        if (iso.find() && iso.find()) {
            return iso.group(1);
        }
        Matcher fr = FR_DATE.matcher(text);
        if (fr.find() && fr.find()) {
            return toIso(fr.group(1), fr.group(2), fr.group(3));
        }
        return null;
    }

    private static String toIso(String d, String m, String y) {
        try {
            return LocalDate.of(Integer.parseInt(y), Integer.parseInt(m), Integer.parseInt(d)).format(ISO);
        } catch (DateTimeParseException | NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal amountFor(String text, String label) {
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(label)) {
                try {
                    return new BigDecimal(m.group(2).replace(',', '.'));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
