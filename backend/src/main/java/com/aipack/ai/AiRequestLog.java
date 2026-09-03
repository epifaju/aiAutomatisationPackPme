package com.aipack.ai;

import com.aipack.common.persistence.BaseEntity;
import com.aipack.identity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_requests")
public class AiRequestLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(length = 128)
    private String model;

    @Column(nullable = false, length = 64)
    private String purpose;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "error_message")
    private String errorMessage;
}
