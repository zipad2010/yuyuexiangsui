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

    @Transactional
    public void saveTurn(Long userId, String userMessage, String assistantMessage) {
        save(userId, "user", userMessage);
        save(userId, "assistant", assistantMessage);
    }

    private void save(Long userId, String role, String content) {
        AiChatHistory item = new AiChatHistory();
        item.setUserId(userId);
        item.setRole(role);
        item.setContent(content);
        repository.save(item);
    }
}