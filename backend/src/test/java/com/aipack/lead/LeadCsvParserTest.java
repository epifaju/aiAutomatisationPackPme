package com.aipack.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aipack.lead.LeadCsvParser.ParsedRow;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeadCsvParserTest {

    private final LeadCsvParser parser = new LeadCsvParser();

    @Test
    void parsesFrenchAliases() {
        String csv =
                """
                nom,email,societe,telephone,notes
                Marie Dupont,marie@demo.aipack.example,Dupont SAS,+33600000000,Prospect chaud
                Paul Martin,paul@demo.aipack.example,,,
                """;
        List<ParsedRow> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).ok()).isTrue();
        assertThat(rows.get(0).request().fullName()).isEqualTo("Marie Dupont");
        assertThat(rows.get(0).request().source()).isEqualTo("CSV");
        assertThat(rows.get(0).request().companyName()).isEqualTo("Dupont SAS");
        assertThat(rows.get(1).request().email()).isEqualTo("paul@demo.aipack.example");
    }

    @Test
    void parsesSemicolonDelimiter() {
        String csv = "nom;email\nClara;clara@demo.aipack.example\n";
        List<ParsedRow> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).request().fullName()).isEqualTo("Clara");
    }

    @Test
    void marksInvalidEmailAsRowError() {
        String csv = "fullName,email\nAda Lovelace,not-an-email\n";
        List<ParsedRow> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).ok()).isFalse();
        assertThat(rows.get(0).error()).containsIgnoringCase("email");
    }

    @Test
    void rejectsMissingFullNameColumn() {
        String csv = "email\nmarie@demo.aipack.example\n";
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(LeadException.class)
                .extracting(ex -> ((LeadException) ex).getCode())
                .isEqualTo("INVALID_CSV");
    }

    @Test
    void stripsBomAndAcceptsFullNameHeader() {
        String csv = "\uFEFFfullName,email\nGrace Hopper,grace@demo.aipack.example\n";
        List<ParsedRow> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).request().fullName()).isEqualTo("Grace Hopper");
    }
}
