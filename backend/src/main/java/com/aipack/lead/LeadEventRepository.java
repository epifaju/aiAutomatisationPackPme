package com.aipack.lead;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadEventRepository extends JpaRepository<LeadEvent, UUID> {

    List<LeadEvent> findByLeadIdOrderByOccurredAtAsc(UUID leadId);
}
