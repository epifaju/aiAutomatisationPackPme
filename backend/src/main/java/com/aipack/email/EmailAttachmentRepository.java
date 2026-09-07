package com.aipack.email;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, UUID> {

    List<EmailAttachment> findByEmail_IdAndCompany_IdOrderByCreatedAtAsc(UUID emailId, UUID companyId);

    Optional<EmailAttachment> findByIdAndEmail_IdAndCompany_Id(UUID id, UUID emailId, UUID companyId);
}
