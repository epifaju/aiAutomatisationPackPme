package com.aipack.email;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAnalysisRepository extends JpaRepository<EmailAnalysis, UUID> {

    Optional<EmailAnalysis> findByEmailId(UUID emailId);
}
