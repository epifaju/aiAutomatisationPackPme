package com.aipack.lead;

import com.aipack.common.persistence.BaseEntity;
import com.aipack.identity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "leads")
public class Lead extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 32)
    private String status = LeadStatus.NEW.name();

    @Column(length = 320)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "company_name")
    private String companyName;

    @Column(length = 64)
    private String phone;

    @Column(nullable = false)
    private int score;

    private String summary;

    @Column(name = "probable_need")
    private String probableNeed;

    @Column(length = 32)
    private String urgency;

    @Column(name = "potential_budget", length = 64)
    private String potentialBudget;

    @Column(name = "recommended_action")
    private String recommendedAction;

    @Column(name = "ai_status", length = 32)
    private String aiStatus;

    @Column(name = "confidence_score", precision = 4, scale = 3)
    private BigDecimal confidenceScore;
}
