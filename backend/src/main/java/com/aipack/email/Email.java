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
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "emails")
public class Email extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "message_id", length = 998)
    private String messageId;

    @Column(name = "from_address", nullable = false, length = 320)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 320)
    private String toAddress;

    @Column(length = 998)
    private String subject;

    @Column(name = "body_text")
    private String bodyText;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false, length = 32)
    private String status = EmailStatus.RECEIVED.name();

    @OneToOne(mappedBy = "email", fetch = FetchType.LAZY)
    private EmailAnalysis analysis;
}
