package com.voice.repository;

import com.voice.model.ForumPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {
    @Query("SELECT p FROM ForumPost p ORDER BY p.createdAt DESC")
    List<ForumPost> findAllOrderByTime(Pageable pageable);
    
    List<ForumPost> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}