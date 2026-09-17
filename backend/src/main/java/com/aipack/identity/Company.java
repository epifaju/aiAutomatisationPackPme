package com.aipack.identity;

import com.aipack.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String siret;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false)
    private String timezone;

    /** SHA-256 hex of the per-company webhook secret (never the plaintext). */
    @Column(name = "webhook_secret_hash", length = 64)
    private String webhookSecretHash;
}
