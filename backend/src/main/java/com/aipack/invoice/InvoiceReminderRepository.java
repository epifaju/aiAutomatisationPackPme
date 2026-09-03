package com.aipack.invoice;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceReminderRepository extends JpaRepository<InvoiceReminder, UUID> {

    Optional<InvoiceReminder> findByInvoice_IdAndReminderLevel(UUID invoiceId, int reminderLevel);

    long countByCompany_IdAndStatusAndSentAtGreaterThanEqualAndSentAtLessThan(
            UUID companyId, String status, Instant from, Instant to);
}
