package com.debate.repository;

import com.debate.entity.Debate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DebateRepository extends JpaRepository<Debate, Long> {
    List<Debate> findAllByOrderByCreatedAtDesc();
}
