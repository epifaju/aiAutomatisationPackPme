package com.aipack.identity;

import com.aipack.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "company_settings")
public class CompanySettings extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "lead_score_low_max", nullable = false)
    private int leadScoreLowMax = 30;

    @Column(name = "lead_score_medium_max", nullable = false)
    private int leadScoreMediumMax = 60;

    @Column(name = "lead_score_high_max", nullable = false)
    private int leadScoreHighMax = 80;

    @Column(name = "lead_confidence_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal leadConfidenceThreshold = new BigDecimal("0.700");

    @Column(name = "ai_generated_email_auto_send", nullable = false)
    private boolean aiGeneratedEmailAutoSend;

    @Column(name = "email_confidence_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal emailConfidenceThreshold = new BigDecimal("0.700");

    @Column(name = "document_confidence_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal documentConfidenceThreshold = new BigDecimal("0.700");

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "invoice_reminder_days", columnDefinition = "integer[]")
    private Integer[] invoiceReminderDays = new Integer[] {3, 7, 15, 30};

    @Column(name = "invoice_reminder_auto_send", nullable = false)
    private boolean invoiceReminderAutoSend;

    @Column(name = "daily_report_auto_send", nullable = false)
    private boolean dailyReportAutoSend;

    @Column(name = "daily_report_email", length = 320)
    private String dailyReportEmail;

    @Column(name = "ai_provider", nullable = false, length = 64)
    private String aiProvider = "OLLAMA";

    @Column(name = "ollama_base_url", nullable = false, length = 512)
    private String ollamaBaseUrl = "http://ollama:11434";

    @Column(name = "ollama_model", nullable = false, length = 128)
    private String ollamaModel = "llama3.2";

    @Column(name = "data_retention_days", nullable = false)
    private int dataRetentionDays = 365;
}
