package com.aipack.email;

import com.aipack.common.persistence.BaseEntity;
import com.aipack.identity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "email_analysis")
public class EmailAnalysis extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_id", nullable = false, unique = true)
    private Email email;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 16)
    private String priority;

    @Column(length = 128)
    private String intent;

    private String summary;

    @Column(name = "suggested_reply")
    private String suggestedReply;

    @Column(name = "confidence_score", precision = 4, scale = 3)
    private BigDecimal confidenceScore;

    @Column(nullable = false, length = 32)
    private String status = EmailAnalysisStatus.COMPLETED.name();

    @Column(name = "approval_status", length = 32)
    private String approvalStatus;
}
