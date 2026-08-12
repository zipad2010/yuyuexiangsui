package com.voice.service;

import com.voice.model.AiChatHistory;
import com.voice.repository.AiChatHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiChatHistoryService {
    @Autowired
    private AiChatHistoryRepository repository;

    /** 兼容旧接口：全部历史（无会话维度） */
    public List<AiChatHistory> getAll(Long userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    public List<AiChatHistory> getRecent(Long userId, int maxMessages) {
        List<AiChatHistory> history = repository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, maxMessages));
        Collections.reverse(history);
        return history;
    }

    public List<Map<String, Object>> getAllViews(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiChatHistory item : getAll(userId)) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("role", item.getRole());
            view.put("content", item.getContent());
            view.put("createdAt", item.getCreatedAt().getTime());
            result.add(view);
        }
        return result;
    }

    /** 指定会话的历史（供聊天上下文） */
    public List<AiChatHistory> getConversationHistory(Long userId, Long conversationId, int maxMessages) {
        if (conversationId == null || conversationId <= 0) {
            return getRecent(userId, maxMessages);
        }
        List<AiChatHistory> history = repository.findByUserIdAndConversationIdOrderByCreatedAtDesc(
                userId, conversationId, PageRequest.of(0, maxMessages));
        Collections.reverse(history);
        return history;
    }

    /** 指定会话的完整历史（前端展示用） */
    public List<Map<String, Object>> getConversationViews(Long userId, Long conversationId) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<AiChatHistory> items = conversationId == null || conversationId <= 0
                ? repository.findByUserIdOrderByCreatedAtAsc(userId)
                : repository.findByUserIdAndConversationIdOrderByCreatedAtAsc(userId, conversationId);
        for (AiChatHistory item : items) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", item.getId());
            view.put("role", item.getRole());
            view.put("content", item.getContent());
            view.put("conversationId", item.getConversationId());
            view.put("createdAt", item.getCreatedAt().getTime());
            result.add(view);
        }
        return result;
    }

    /**
     * 撤回一条消息：删除指定历史记录；若撤回的是用户消息，
     * 同时删除同一会话中紧随其后的 AI 回复（保证上下文成对）。
     * @return 是否删除成功（存在且归属正确）
     */
    @Transactional
    public boolean deleteMessage(Long userId, Long historyId) {
        if (historyId == null) {
            return false;
        }
        AiChatHistory target = repository.findByIdAndUserId(historyId, userId).orElse(null);
        if (target == null) {
            return false;
        }
        Long conversationId = target.getConversationId() == null ? 0L : target.getConversationId();
        if ("user".equals(target.getRole())) {
            // 删除紧随其后的 assistant 回复
            List<AiChatHistory> followers = repository.findAssistantAfter(
                    userId, conversationId, target.getCreatedAt());
            if (!followers.isEmpty()) {
                repository.delete(followers.get(0));
            }
        }
        repository.delete(target);
        return true;
    }

    @Transactional
    public void saveTurn(Long userId, String userMessage, String assistantMessage) {
        save(userId, "user", userMessage, null);
        save(userId, "assistant", assistantMessage, null);
    }

    /** 保存一轮对话到指定会话 */
    @Transactional
    public void saveTurn(Long userId, Long conversationId, String userMessage, String assistantMessage) {
        if (conversationId == null || conversationId <= 0) {
            saveTurn(userId, userMessage, assistantMessage);
            return;
        }
        save(userId, "user", userMessage, conversationId);
        save(userId, "assistant", assistantMessage, conversationId);
    }

    private void save(Long userId, String role, String content, Long conversationId) {
        AiChatHistory item = new AiChatHistory();
        item.setUserId(userId);
        item.setRole(role);
        item.setContent(content);
        item.setConversationId(conversationId);
        repository.save(item);
    }

    /** 把旧数据（conversationId 为 null）归入默认会话 0 */
    @Transactional
    public void migrateNullConversationToDefault(Long userId) {
        repository.migrateNullConversationToDefault(userId);
    }
}