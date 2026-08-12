package com.voice.repository;

import com.voice.model.Persona;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PersonaRepository extends JpaRepository<Persona, Long> {
    List<Persona> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);
    List<Persona> findByStatusAndAuthorIdOrderByCreatedAtDesc(Integer status, Long authorId);
}
