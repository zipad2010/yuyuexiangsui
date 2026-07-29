package com.voice.repository;

import com.voice.model.AiMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AiMemoryRepository extends JpaRepository<AiMemory, Long> {
    Optional<AiMemory> findByUserId(Long userId);
}