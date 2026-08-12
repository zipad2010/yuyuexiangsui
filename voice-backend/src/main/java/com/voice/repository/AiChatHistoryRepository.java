package com.voice.repository;

import com.voice.model.AiChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface AiChatHistoryRepository extends JpaRepository<AiChatHistory, Long> {
    List<AiChatHistory> findByUserIdOrderByCreatedAtAsc(Long userId);
    List<AiChatHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<AiChatHistory> findByUserIdAndConversationIdOrderByCreatedAtAsc(Long userId, Long conversationId);

    List<AiChatHistory> findByUserIdAndConversationIdOrderByCreatedAtDesc(
            Long userId, Long conversationId, Pageable pageable);

    /** 统计某用户在指定会话的条目数（用于判断会话是否为空） */
    long countByUserIdAndConversationId(Long userId, Long conversationId);

    /** 会话切换时需要把旧数据归入默认会话（conversationId = 0 表示默认） */
    @Query("UPDATE AiChatHistory h SET h.conversationId = 0 WHERE h.userId = :userId AND h.conversationId IS NULL")
    void migrateNullConversationToDefault(@Param("userId") Long userId);

    /** 撤回消息：按 id + userId 查找（校验归属） */
    java.util.Optional<AiChatHistory> findByIdAndUserId(Long id, Long userId);

    /** 删除指定会话的全部历史（删除会话时级联） */
    @Modifying
    @Transactional
    @Query("DELETE FROM AiChatHistory h WHERE h.userId = :userId AND h.conversationId = :conversationId")
    void deleteByUserIdAndConversationId(@Param("userId") Long userId,
            @Param("conversationId") Long conversationId);

    /** 撤回用户消息后，删除同一会话中紧随其后的 assistant 回复 */
    @Query("SELECT h FROM AiChatHistory h WHERE h.userId = :userId "
            + "AND h.conversationId = :conversationId AND h.role = 'assistant' "
            + "AND h.createdAt > :createdAt ORDER BY h.createdAt ASC")
    List<AiChatHistory> findAssistantAfter(@Param("userId") Long userId,
            @Param("conversationId") Long conversationId,
            @Param("createdAt") java.util.Date createdAt);
}