package com.aipack.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aipack.ai.AiParsingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DailyReportSummaryParserTest {

    private final DailyReportSummaryParser parser = new DailyReportSummaryParser(new ObjectMapper());

    @Test
    void extractsSummaryFromJson() {
        assertThat(parser.parse("{\"summary\":\"  Activité calme.  \"}")).isEqualTo("Activité calme.");
    }

    @Test
    void stripsMarkdownFences() {
        String raw =
                """
                ```json
                {"summary":"Deux factures en retard."}
                ```
                """;
        assertThat(parser.parse(raw)).isEqualTo("Deux factures en retard.");
    }

    @Test
    void rejectsMissingSummary() {
        assertThatThrownBy(() -> parser.parse("{\"summary\":\"\"}")).isInstanceOf(AiParsingException.class);
        assertThatThrownBy(() -> parser.parse("pas du json")).isInstanceOf(AiParsingException.class);
        assertThatThrownBy(() -> parser.parse("   ")).isInstanceOf(AiParsingException.class);
    }
}
