package com.debate.repository;

import com.debate.entity.DebateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DebateMessageRepository extends JpaRepository<DebateMessage, Long> {
    List<DebateMessage> findByDebateIdOrderBySequenceAsc(Long debateId);
}
