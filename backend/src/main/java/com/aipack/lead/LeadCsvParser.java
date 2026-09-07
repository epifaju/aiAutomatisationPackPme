package com.aipack.lead;

import com.aipack.lead.dto.CreateLeadRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class LeadCsvParser {

    static final int MAX_ROWS = 500;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Set<String> FULL_NAME = Set.of("fullname", "full_name", "nom", "name", "nom_complet");
    private static final Set<String> EMAIL = Set.of("email", "mail", "e_mail", "courriel");
    private static final Set<String> COMPANY =
            Set.of("companyname", "company_name", "company", "societe", "entreprise");
    private static final Set<String> PHONE = Set.of("phone", "telephone", "tel", "mobile");
    private static final Set<String> SUMMARY = Set.of("summary", "notes", "note", "commentaire", "comment");

    public List<ParsedRow> parse(InputStream inputStream) {
        byte[] all;
        try {
            all = inputStream.readAllBytes();
        } catch (IOException ex) {
            throw LeadException.invalidCsv("Impossible de lire le fichier CSV");
        }
        if (all.length == 0) {
            throw LeadException.emptyCsv();
        }
        String text = new String(all, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        text = text.trim();
        if (text.isEmpty()) {
            throw LeadException.emptyCsv();
        }

        String firstHeaderLine = text.lines().map(String::trim).filter(s -> !s.isEmpty()).findFirst().orElse("");
        char delimiter = detectDelimiter(firstHeaderLine);
        String[] headerNames = java.util.Arrays.stream(firstHeaderLine.split(Pattern.quote(String.valueOf(delimiter)), -1))
                .map(String::trim)
                .toArray(String[]::new);
        Map<String, String> headerMap = mapHeaders(java.util.Arrays.asList(headerNames));
        if (!headerMap.containsKey("fullName")) {
            throw LeadException.invalidCsv(
                    "Colonne obligatoire manquante : fullName (aliases : nom, name, full_name)");
        }
        try (Reader reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            CSVFormat format = CSVFormat.DEFAULT
                    .builder()
                    .setDelimiter(delimiter)
                    .setHeader(headerNames)
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .build();
            try (CSVParser parser = format.parse(reader)) {
                List<ParsedRow> rows = new ArrayList<>();
                int dataRows = 0;
                for (CSVRecord record : parser) {
                    if (isBlankRecord(record)) {
                        continue;
                    }
                    dataRows++;
                    if (dataRows > MAX_ROWS) {
                        throw LeadException.importTooLarge();
                    }
                    int rowNumber = (int) record.getRecordNumber();
                    rows.add(toRow(record, headerMap, rowNumber));
                }
                if (rows.isEmpty()) {
                    throw LeadException.emptyCsv();
                }
                return rows;
            }
        } catch (LeadException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw LeadException.invalidCsv("CSV illisible : " + ex.getMessage());
        }
    }

    private ParsedRow toRow(CSVRecord record, Map<String, String> headerMap, int rowNumber) {
        String fullName = cell(record, headerMap.get("fullName"));
        String email = cell(record, headerMap.get("email"));
        String companyName = cell(record, headerMap.get("companyName"));
        String phone = cell(record, headerMap.get("phone"));
        String summary = cell(record, headerMap.get("summary"));

        if (fullName == null || fullName.isBlank()) {
            return ParsedRow.invalid(rowNumber, "Nom obligatoire (fullName)");
        }
        if (fullName.length() > 255) {
            return ParsedRow.invalid(rowNumber, "Nom trop long (max 255)");
        }
        if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return ParsedRow.invalid(rowNumber, "Email invalide");
        }
        if (email != null && email.length() > 320) {
            return ParsedRow.invalid(rowNumber, "Email trop long");
        }
        if (companyName != null && companyName.length() > 255) {
            return ParsedRow.invalid(rowNumber, "Société trop longue (max 255)");
        }
        if (phone != null && phone.length() > 64) {
            return ParsedRow.invalid(rowNumber, "Téléphone trop long (max 64)");
        }
        if (summary != null && summary.length() > 8000) {
            return ParsedRow.invalid(rowNumber, "Notes trop longues (max 8000)");
        }

        CreateLeadRequest request = new CreateLeadRequest(
                LeadSource.CSV.name(),
                LeadStatus.NEW.name(),
                blankToNull(email),
                fullName.trim(),
                blankToNull(companyName),
                blankToNull(phone),
                0,
                blankToNull(summary));
        return ParsedRow.valid(rowNumber, request);
    }

    private static Map<String, String> mapHeaders(Iterable<String> headers) {
        Map<String, String> mapped = new HashMap<>();
        for (String raw : headers) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String norm = normalizeHeader(raw);
            if (FULL_NAME.contains(norm)) {
                mapped.putIfAbsent("fullName", raw);
            } else if (EMAIL.contains(norm)) {
                mapped.putIfAbsent("email", raw);
            } else if (COMPANY.contains(norm)) {
                mapped.putIfAbsent("companyName", raw);
            } else if (PHONE.contains(norm)) {
                mapped.putIfAbsent("phone", raw);
            } else if (SUMMARY.contains(norm)) {
                mapped.putIfAbsent("summary", raw);
            }
        }
        return mapped;
    }

    private static String normalizeHeader(String header) {
        return header.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replace("é", "e")
                .replace("è", "e")
                .replace("ê", "e");
    }

    private static char detectDelimiter(String firstLine) {
        long commas = firstLine.chars().filter(c -> c == ',').count();
        long semis = firstLine.chars().filter(c -> c == ';').count();
        return semis > commas ? ';' : ',';
    }

    private static boolean isBlankRecord(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(CSVRecord record, String header) {
        if (header == null || !record.isMapped(header)) {
            return null;
        }
        try {
            return record.get(header);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ParsedRow(int rowNumber, CreateLeadRequest request, String error) {
        static ParsedRow valid(int row, CreateLeadRequest request) {
            return new ParsedRow(row, request, null);
        }

        static ParsedRow invalid(int row, String error) {
            return new ParsedRow(row, null, error);
        }

        boolean ok() {
            return error == null && request != null;
        }
    }
}
