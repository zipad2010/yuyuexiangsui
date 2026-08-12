package com.voice.repository;

import com.voice.model.PersonaSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PersonaSubscriptionRepository extends JpaRepository<PersonaSubscription, Long> {
    Optional<PersonaSubscription> findByUserIdAndPersonaId(Long userId, Long personaId);
    boolean existsByUserIdAndPersonaId(Long userId, Long personaId);
    List<PersonaSubscription> findByUserId(Long userId);
}
