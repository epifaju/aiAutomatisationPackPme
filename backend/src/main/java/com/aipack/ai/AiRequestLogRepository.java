package com.aipack.ai;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, UUID> {}
