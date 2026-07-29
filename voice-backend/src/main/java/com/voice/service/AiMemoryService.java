package com.voice.service;

import com.voice.model.AiMemory;
import com.voice.repository.AiMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;

@Service
public class AiMemoryService {
    @Autowired
    private AiMemoryRepository repository;

    @Value("${ai.memory.max-characters:1600}")
    private int maxCharacters;

    public String getSummary(Long userId) {
        AiMemory memory = repository.findByUserId(userId).orElse(null);
        return memory == null || memory.getSummary() == null ? "" : memory.getSummary();
    }

    @Transactional
    public void rememberTurn(Long userId, String userMessage, String assistantMessage) {
        AiMemory memory = repository.findByUserId(userId).orElseGet(() -> {
            AiMemory created = new AiMemory();
            created.setUserId(userId);
            return created;
        });
        String entry = "用户：" + compact(userMessage, 240)
                + "\n助手：" + compact(assistantMessage, 360);
        String combined = memory.getSummary().isEmpty()
                ? entry : memory.getSummary() + "\n" + entry;
        if (combined.length() > maxCharacters) {
            combined = combined.substring(combined.length() - maxCharacters);
            int firstBreak = combined.indexOf('\n');
            if (firstBreak >= 0 && firstBreak < combined.length() - 1) {
                combined = combined.substring(firstBreak + 1);
            }
        }
        memory.setSummary(combined);
        memory.setUpdatedAt(new Date());
        repository.save(memory);
    }

    @Transactional
    public void saveSummary(Long userId, String summary) {
        AiMemory memory = repository.findByUserId(userId).orElseGet(() -> {
            AiMemory created = new AiMemory();
            created.setUserId(userId);
            return created;
        });
        String compacted = summary == null ? "" : summary.trim();
        if (compacted.length() > maxCharacters) {
            compacted = compacted.substring(0, maxCharacters);
        }
        memory.setSummary(compacted);
        memory.setUpdatedAt(new Date());
        repository.save(memory);
    }

    private String compact(String value, int limit) {
        String compacted = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return compacted.length() <= limit ? compacted : compacted.substring(0, limit) + "…";
    }
}