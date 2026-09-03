package com.aipack.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LeadQualificationParserTest {

    private final LeadQualificationParser parser = new LeadQualificationParser(new ObjectMapper());

    @Test
    void parsesStructuredJson() {
        LeadQualificationParser.Result result = parser.parse(
                """
                {
                  "score": 81,
                  "summary": "Relances factures",
                  "probableNeed": "Automatisation",
                  "urgency": "high",
                  "potentialBudget": "10kEUR",
                  "recommendedAction": "Appeler",
                  "confidenceScore": 0.91
                }
                """);
        assertThat(result.score()).isEqualTo(81);
        assertThat(result.urgency()).isEqualTo("HIGH");
        assertThat(result.confidenceScore()).isEqualByComparingTo(new BigDecimal("0.91"));
    }

    @Test
    void extractsJsonFromMarkdownFence() {
        LeadQualificationParser.Result result = parser.parse(
                """
                ```json
                {"score":10,"summary":"ok","urgency":"LOW","confidenceScore":0.5}
                ```
                """);
        assertThat(result.score()).isEqualTo(10);
        assertThat(result.summary()).isEqualTo("ok");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("pas du json")).isInstanceOf(AiParsingException.class);
    }

    @Test
    void rejectsOutOfRangeScore() {
        assertThatThrownBy(() -> parser.parse(
                        """
                        {"score":120,"summary":"x","urgency":"LOW","confidenceScore":0.5}
                        """))
                .isInstanceOf(AiParsingException.class);
    }

    @Test
    void rejectsUnknownUrgency() {
        assertThatThrownBy(() -> parser.parse(
                        """
                        {"score":10,"summary":"x","urgency":"SOON","confidenceScore":0.5}
                        """))
                .isInstanceOf(AiParsingException.class);
    }
}
