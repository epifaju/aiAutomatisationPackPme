package com.aipack;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class SchemaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg16");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsAreApplied() {
        Integer latest = jdbcTemplate.queryForObject(
                "SELECT max(installed_rank) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(latest).isGreaterThanOrEqualTo(3);
    }

    @Test
    void domainTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """,
                String.class);
        assertThat(tables)
                .contains(
                        "companies",
                        "users",
                        "company_settings",
                        "emails",
                        "email_analysis",
                        "leads",
                        "lead_events",
                        "documents",
                        "document_extractions",
                        "customers",
                        "invoices",
                        "invoice_reminders",
                        "workflow_runs",
                        "audit_logs",
                        "ai_requests",
                        "notifications");
    }

    @Test
    void demoSeedMatchesPrdMinimums() {
        assertThat(count("companies")).isEqualTo(1);
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("leads")).isEqualTo(10);
        assertThat(count("emails")).isEqualTo(10);
        assertThat(count("invoices")).isEqualTo(10);
        assertThat(count("documents")).isEqualTo(5);
        assertThat(count("audit_logs")).isEqualTo(20);
    }

    @Test
    void invoiceReminderUniquenessIsEnforced() {
        Integer uniqueConstraints = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM pg_constraint
                WHERE conname = 'uq_invoice_reminders_level' AND contype = 'u'
                """,
                Integer.class);
        assertThat(uniqueConstraints).isEqualTo(1);
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
