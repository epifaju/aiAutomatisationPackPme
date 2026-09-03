package com.aipack.invoice;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByIdAndCompany_Id(UUID id, UUID companyId);

    Optional<Invoice> findByCompany_IdAndInvoiceNumber(UUID companyId, String invoiceNumber);

    List<Invoice> findByCompany_IdAndStatusIn(UUID companyId, Collection<String> statuses);

    long countByCustomer_Id(UUID customerId);

    long countByCompany_IdAndStatus(UUID companyId, String status);

    @Query(
            """
            SELECT coalesce(sum(i.amountIncludingTax), 0)
            FROM Invoice i
            WHERE i.company.id = :companyId AND i.status = :status
            """)
    BigDecimal sumAmountIncludingTaxByCompanyAndStatus(
            @Param("companyId") UUID companyId, @Param("status") String status);
}
