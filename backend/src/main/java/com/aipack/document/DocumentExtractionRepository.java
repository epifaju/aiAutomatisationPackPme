package com.aipack.document;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, UUID> {

    Optional<DocumentExtraction> findByDocumentId(UUID documentId);
}
