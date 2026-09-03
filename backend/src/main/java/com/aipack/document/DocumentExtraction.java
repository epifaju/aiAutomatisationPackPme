package com.aipack.document;

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
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "document_extractions")
public class DocumentExtraction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_json", nullable = false)
    private Map<String, Object> extractedJson = new LinkedHashMap<>();

    @Column(name = "extracted_text")
    private String extractedText;

    @Column(name = "confidence_score", precision = 4, scale = 3)
    private BigDecimal confidenceScore;

    @Column(nullable = false, length = 32)
    private String status = DocumentExtractionStatus.COMPLETED.name();
}
