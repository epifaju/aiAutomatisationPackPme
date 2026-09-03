package com.aipack.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptCatalog {

    private final String leadQualification;
    private final String emailClassification;
    private final String emailResponse;
    private final String documentExtraction;
    private final String dailyReport;

    public PromptCatalog() {
        this.leadQualification = read("prompts/lead-qualification.txt");
        this.emailClassification = read("prompts/email-classification.txt");
        this.emailResponse = read("prompts/email-response.txt");
        this.documentExtraction = read("prompts/document-extraction.txt");
        this.dailyReport = read("prompts/daily-report.txt");
    }

    public String leadQualification(Map<String, String> variables) {
        return apply(leadQualification, variables);
    }

    public String emailClassification(Map<String, String> variables) {
        return apply(emailClassification, variables);
    }

    public String emailResponse(Map<String, String> variables) {
        return apply(emailResponse, variables);
    }

    public String documentExtraction(Map<String, String> variables) {
        return apply(documentExtraction, variables);
    }

    public String dailyReport(Map<String, String> variables) {
        return apply(dailyReport, variables);
    }

    static String apply(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }

    private static String read(String classpathLocation) {
        try {
            return new ClassPathResource(classpathLocation).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Prompt introuvable : " + classpathLocation, ex);
        }
    }
}
