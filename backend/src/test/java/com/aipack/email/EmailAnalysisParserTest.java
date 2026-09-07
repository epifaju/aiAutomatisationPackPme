package com.aipack.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EmailAnalysisParserTest {

    private final EmailAnalysisParser parser = new EmailAnalysisParser(new ObjectMapper());

    @Test
    void parsesClassificationJson() {
        EmailAnalysisParser.Classification result = parser.parseClassification(
                """
                {
                  "category": "client",
                  "priority": "urgent",
                  "intent": "relance facture",
                  "summary": "Le client relance une facture impayée.",
                  "confidenceScore": 0.92
                }
                """);
        assertThat(result.category()).isEqualTo("CLIENT");
        assertThat(result.priority()).isEqualTo("URGENT");
        assertThat(result.intent()).isEqualTo("relance facture");
        assertThat(result.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.92"));
    }

    @Test
    void extractsClassificationFromMarkdownFence() {
        EmailAnalysisParser.Classification result = parser.parseClassification(
                """
                ```json
                {"category":"SPAM","priority":"LOW","summary":"Pub","confidenceScore":0.85}
                ```
                """);
        assertThat(result.category()).isEqualTo("SPAM");
        assertThat(result.summary()).isEqualTo("Pub");
    }

    @Test
    void lowConfidenceSpamBecomesAutre() {
        EmailAnalysisParser.Classification result = parser.parseClassification(
                """
                {"category":"SPAM","priority":"LOW","summary":"Ambigu","confidenceScore":0.4}
                """);
        assertThat(result.category()).isEqualTo("AUTRE");
    }

    @Test
    void unknownCategoryFallsBackToAutre() {
        EmailAnalysisParser.Classification result = parser.parseClassification(
                """
                {"category":"NEWSLETTER","priority":"LOW","summary":"x","confidenceScore":0.5}
                """);
        assertThat(result.category()).isEqualTo("AUTRE");
    }

    @Test
    void missingSummaryGetsDefault() {
        EmailAnalysisParser.Classification result = parser.parseClassification(
                """
                {"category":"CLIENT","priority":"LOW","confidenceScore":0.5}
                """);
        assertThat(result.summary()).isNotBlank();
    }

    @Test
    void parsesSuggestedReply() {
        EmailAnalysisParser.Reply result = parser.parseReply(
                """
                {"suggestedReply":"Bonjour, merci.","confidenceScore":0.7}
                """);
        assertThat(result.suggestedReply()).isEqualTo("Bonjour, merci.");
        assertThat(result.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.7"));
    }

    @Test
    void acceptsReplyAliasAndPercentConfidence() {
        EmailAnalysisParser.Reply result = parser.parseReply(
                """
                {"reply":"OK","confidenceScore":70}
                """);
        assertThat(result.suggestedReply()).isEqualTo("OK");
        assertThat(result.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.7000"));
    }

    @Test
    void rejectsBlankReply() {
        assertThatThrownBy(() -> parser.parseReply("""
                {"suggestedReply":"  "}
                """))
                .isInstanceOf(AiParsingException.class);
    }

    @Test
    void mapsDevisHintToProspect() {
        EmailAnalysisParser.Classification result = parser.parseClassification(
                """
                {"category":"demande_devis","priority":"normale","summary":"Demande de devis","confidenceScore":0.6}
                """);
        assertThat(result.category()).isEqualTo("PROSPECT");
        assertThat(result.priority()).isEqualTo("NORMAL");
    }
}
