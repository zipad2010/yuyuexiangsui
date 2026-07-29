package com.voice.repository;

import com.voice.model.AiChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AiChatHistoryRepository extends JpaRepository<AiChatHistory, Long> {
    List<AiChatHistory> findByUserIdOrderByCreatedAtAsc(Long userId);
    List<AiChatHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}