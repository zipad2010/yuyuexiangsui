package com.voice.repository;

import com.voice.model.PrivateMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    @Query("SELECT m FROM PrivateMessage m WHERE m.fromUserId = :userId OR m.toUserId = :userId ORDER BY m.createdAt DESC")
    List<PrivateMessage> findByUserId(@Param("userId") Long userId);

    @Query("SELECT m FROM PrivateMessage m WHERE (m.fromUserId = :userId1 AND m.toUserId = :userId2) OR (m.fromUserId = :userId2 AND m.toUserId = :userId1) ORDER BY m.createdAt ASC")
    List<PrivateMessage> findConversation(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Modifying
    @Transactional
    @Query("UPDATE PrivateMessage m SET m.isRead = 1 WHERE m.toUserId = :userId AND m.isRead = 0")
    void markAsRead(@Param("userId") Long userId);
}