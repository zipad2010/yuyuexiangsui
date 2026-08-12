package com.voice.service;

import com.voice.model.Conversation;
import com.voice.repository.AiChatHistoryRepository;
import com.voice.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话会话管理：创建 / 列表 / 重命名 / 删除 / 设置人设
 */
@Service
public class ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private AiChatHistoryRepository aiChatHistoryRepository;

    @Transactional
    public Conversation createConversation(Long userId, String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            normalized = "新对话";
        }
        if (normalized.length() > 60) {
            normalized = normalized.substring(0, 60);
        }
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(normalized);
        return conversationRepository.save(conversation);
    }

    public List<Map<String, Object>> listConversations(Long userId) {
        // 迁移旧数据：功能上线前的历史记录 conversationId 为 NULL，归入默认会话(id=0)，
        // 保证老用户切换会话后仍能找回原有对话记录
        migrateLegacyHistory(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        // 头部插入"默认对话"(id=0)：包含迁移后的旧记录
        result.add(defaultConversationView(userId));
        for (Conversation conversation : conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)) {
            result.add(toView(conversation));
        }
        return result;
    }

    /**
     * 把功能上线前的历史记录(conversationId IS NULL)迁移到默认会话(id=0)
     */
    @Transactional
    public void migrateLegacyHistory(Long userId) {
        aiChatHistoryRepository.migrateNullConversationToDefault(userId);
    }

    private Map<String, Object> defaultConversationView(Long userId) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", 0L);
        view.put("title", "默认对话");
        view.put("activePersonaId", null);
        view.put("createdAt", null);
        view.put("updatedAt", null);
        // 标记是否为默认会话（前端展示/防删除）
        view.put("defaultConversation", true);
        return view;
    }

    public Map<String, Object> getConversationView(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return null;
        }
        return toView(conversation);
    }

    public Conversation getOwnedConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            return null;
        }
        return conversation;
    }

    @Transactional
    public Conversation renameConversation(Long userId, Long conversationId, String newTitle) {
        Conversation conversation = getOwnedConversation(userId, conversationId);
        if (conversation == null) {
            return null;
        }
        String normalized = newTitle == null ? "" : newTitle.trim();
        if (normalized.isEmpty()) {
            return conversation;
        }
        conversation.setTitle(normalized.length() > 60 ? normalized.substring(0, 60) : normalized);
        conversation.setUpdatedAt(new java.util.Date());
        return conversationRepository.save(conversation);
    }

    @Transactional
    public Conversation setConversationPersona(Long userId, Long conversationId, Long personaId) {
        Conversation conversation = getOwnedConversation(userId, conversationId);
        if (conversation == null) {
            return null;
        }
        conversation.setActivePersonaId(personaId == null || personaId <= 0 ? null : personaId);
        conversation.setUpdatedAt(new java.util.Date());
        return conversationRepository.save(conversation);
    }

    @Transactional
    public void touchConversation(Long conversationId) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            conversation.setUpdatedAt(new java.util.Date());
            conversationRepository.save(conversation);
        });
    }

    @Transactional
    public boolean deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = getOwnedConversation(userId, conversationId);
        if (conversation == null) {
            return false;
        }
        // 级联删除该会话的历史记录，避免残留
        aiChatHistoryRepository.deleteByUserIdAndConversationId(userId, conversationId);
        conversationRepository.delete(conversation);
        return true;
    }

    private Map<String, Object> toView(Conversation conversation) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", conversation.getId());
        view.put("title", conversation.getTitle());
        view.put("activePersonaId", conversation.getActivePersonaId());
        view.put("createdAt", conversation.getCreatedAt() == null ? null : conversation.getCreatedAt().getTime());
        view.put("updatedAt", conversation.getUpdatedAt() == null ? null : conversation.getUpdatedAt().getTime());
        return view;
    }
}
