package com.aipack.invoice;

import com.aipack.common.persistence.BaseEntity;
import com.aipack.customer.Customer;
import com.aipack.identity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_excluding_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountExcludingTax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vat = BigDecimal.ZERO;

    @Column(name = "amount_including_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountIncludingTax = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(nullable = false, length = 16)
    private String status = InvoiceStatus.DRAFT.name();

    @OneToMany(mappedBy = "invoice")
    @OrderBy("reminderLevel ASC")
    private List<InvoiceReminder> reminders = new ArrayList<>();
}
