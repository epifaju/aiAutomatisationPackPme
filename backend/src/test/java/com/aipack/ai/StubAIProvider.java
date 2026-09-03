package com.aipack.ai;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class StubAIProvider implements AIProvider {

    public static final AtomicReference<String> MODE = new AtomicReference<>("success");

    public static void reset() {
        MODE.set("success");
    }

    @Override
    public AIResponse generate(AIRequest request) {
        return switch (MODE.get()) {
            case "unavailable" -> throw new AiUnavailableException("Ollama indisponible (stub)");
            case "invalid-json" -> new AIResponse("ceci n'est pas du json", "STUB", "stub", 5, false, "SUCCESS");
            case "low-confidence" -> new AIResponse(payload(request.purpose(), 0.20, 40), "STUB", "stub", 5, false, "SUCCESS");
            default -> new AIResponse(payload(request.purpose(), 0.86, 72), "STUB", "stub", 5, false, "SUCCESS");
        };
    }

    private static String payload(String purpose, double confidence, int score) {
        if ("email-classification".equals(purpose)) {
            return """
                    {
                      "category": "CLIENT",
                      "priority": "HIGH",
                      "intent": "demande-devis",
                      "summary": "Le client demande un devis pour l'automatisation.",
                      "confidenceScore": %s
                    }
                    """
                    .formatted(Double.toString(confidence));
        }
        if ("email-response".equals(purpose)) {
            return """
                    {
                      "suggestedReply": "Bonjour,\\n\\nMerci pour votre message. Nous revenons vers vous avec une proposition.\\n\\nCordialement",
                      "confidenceScore": %s
                    }
                    """
                    .formatted(Double.toString(confidence));
        }
        if ("document-extraction".equals(purpose)) {
            return """
                    {
                      "documentType": "FACTURE",
                      "supplier": "Fournisseur Démo",
                      "customer": "Demo SAS",
                      "invoiceNumber": "F-2026-001",
                      "invoiceDate": "2026-03-01",
                      "dueDate": "2026-03-31",
                      "amountExcludingTax": 100.00,
                      "vat": 20.00,
                      "amountIncludingTax": 120.00,
                      "currency": "EUR",
                      "summary": "Facture de prestation d'automatisation.",
                      "confidenceScore": %s
                    }
                    """
                    .formatted(Double.toString(confidence));
        }
        if ("daily-report".equals(purpose)) {
            return """
                    {
                      "summary": "Journée d'activité : quelques emails dont des urgents, de nouveaux prospects, et un suivi des factures en retard."
                    }
                    """;
        }
        return """
                {
                  "score": %d,
                  "summary": "Besoin d'automatiser le suivi administratif.",
                  "probableNeed": "Automatisation administrative",
                  "urgency": "HIGH",
                  "potentialBudget": "5-10kEUR",
                  "recommendedAction": "Planifier un appel de découverte",
                  "confidenceScore": %s
                }
                """
                .formatted(score, Double.toString(confidence));
    }
}
