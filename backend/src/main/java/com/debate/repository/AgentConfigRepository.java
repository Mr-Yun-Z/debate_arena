package com.debate.repository;

import com.debate.entity.AgentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {
    Optional<AgentConfig> findByRole(String role);
}
