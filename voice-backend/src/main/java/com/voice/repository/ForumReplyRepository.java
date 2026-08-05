package com.voice.repository;

import com.voice.model.ForumReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ForumReplyRepository extends JpaRepository<ForumReply, Long> {
    List<ForumReply> findByPostIdOrderByCreatedAtAsc(Long postId);
    List<ForumReply> findByPostId(Long postId);

    @Query("SELECT COUNT(r) FROM ForumReply r WHERE r.postId IN "
            + "(SELECT p.id FROM ForumPost p WHERE p.userId = :userId) "
            + "AND r.userId <> :userId AND r.createdAt > :lastCheckedAt")
    long countNewRepliesForPostOwner(@Param("userId") Long userId,
            @Param("lastCheckedAt") java.util.Date lastCheckedAt);
}