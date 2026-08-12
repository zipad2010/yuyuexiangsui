package com.voice.service;

import com.voice.model.Persona;
import com.voice.model.PersonaSubscription;
import com.voice.repository.PersonaRepository;
import com.voice.repository.PersonaSubscriptionRepository;
import com.voice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人设投稿中心：发布 / 浏览 / 订阅 / 获取已订阅人设
 */
@Service
public class PersonaService {

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private PersonaSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public Persona createPersona(Long authorId, String name, String description, String prompt) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("人设名称不能为空");
        }
        if (normalizedPrompt.isEmpty()) {
            throw new IllegalArgumentException("人设内容不能为空");
        }
        Persona persona = new Persona();
        persona.setAuthorId(authorId);
        persona.setName(normalizedName.length() > 60 ? normalizedName.substring(0, 60) : normalizedName);
        String normalizedDesc = description == null ? "" : description.trim();
        persona.setDescription(normalizedDesc.length() > 250 ? normalizedDesc.substring(0, 250) : normalizedDesc);
        persona.setPrompt(normalizedPrompt);
        persona.setSubscribeCount(0);
        persona.setStatus(1);
        return personaRepository.save(persona);
    }

    public List<Map<String, Object>> listPersonas(int page, int size, Long viewerId) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Persona persona : personaRepository.findByStatusOrderByCreatedAtDesc(1, pageRequest)) {
            result.add(toView(persona, viewerId));
        }
        return result;
    }

    public List<Map<String, Object>> listMyPersonas(Long authorId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Persona persona : personaRepository.findByStatusAndAuthorIdOrderByCreatedAtDesc(1, authorId)) {
            result.add(toView(persona, authorId));
        }
        return result;
    }

    public Map<String, Object> getPersonaView(Long personaId, Long viewerId) {
        Persona persona = personaRepository.findById(personaId).orElse(null);
        if (persona == null || !Integer.valueOf(1).equals(persona.getStatus())) {
            return null;
        }
        return toView(persona, viewerId);
    }

    /** 订阅后才能获取完整人设 prompt */
    public String getSubscribedPrompt(Long userId, Long personaId) {
        Persona persona = personaRepository.findById(personaId).orElse(null);
        if (persona == null || !Integer.valueOf(1).equals(persona.getStatus())) {
            return null;
        }
        boolean subscribed = subscriptionRepository.existsByUserIdAndPersonaId(userId, personaId);
        if (subscribed) {
            return persona.getPrompt();
        }
        return null;
    }

    public boolean isSubscribed(Long userId, Long personaId) {
        return personaId != null && subscriptionRepository.existsByUserIdAndPersonaId(userId, personaId);
    }

    @Transactional
    public boolean subscribe(Long userId, Long personaId) {
        Persona persona = personaRepository.findById(personaId).orElse(null);
        if (persona == null || !Integer.valueOf(1).equals(persona.getStatus())) {
            return false;
        }
        if (subscriptionRepository.existsByUserIdAndPersonaId(userId, personaId)) {
            return true; // 已订阅
        }
        PersonaSubscription subscription = new PersonaSubscription();
        subscription.setUserId(userId);
        subscription.setPersonaId(personaId);
        subscriptionRepository.save(subscription);

        persona.setSubscribeCount(persona.getSubscribeCount() == null ? 1 : persona.getSubscribeCount() + 1);
        personaRepository.save(persona);
        return true;
    }

    @Transactional
    public boolean unsubscribe(Long userId, Long personaId) {
        subscriptionRepository.findByUserIdAndPersonaId(userId, personaId).ifPresent(subscription -> {
            subscriptionRepository.delete(subscription);
            personaRepository.findById(personaId).ifPresent(persona -> {
                int count = persona.getSubscribeCount() == null ? 0 : persona.getSubscribeCount();
                persona.setSubscribeCount(Math.max(0, count - 1));
                personaRepository.save(persona);
            });
        });
        return true;
    }

    /** 用户已订阅的人设列表（用于创建/切换对话时选择） */
    public List<Map<String, Object>> listSubscribedPersonas(Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PersonaSubscription subscription : subscriptionRepository.findByUserId(userId)) {
            Persona persona = personaRepository.findById(subscription.getPersonaId()).orElse(null);
            if (persona == null || !Integer.valueOf(1).equals(persona.getStatus())) {
                continue;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", persona.getId());
            view.put("name", persona.getName());
            view.put("description", persona.getDescription());
            view.put("authorId", persona.getAuthorId());
            view.put("authorName", getUsername(persona.getAuthorId()));
            view.put("subscribeCount", persona.getSubscribeCount());
            view.put("prompt", persona.getPrompt());
            result.add(view);
        }
        return result;
    }

    private Map<String, Object> toView(Persona persona, Long viewerId) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", persona.getId());
        view.put("name", persona.getName());
        view.put("description", persona.getDescription());
        view.put("authorId", persona.getAuthorId());
        view.put("authorName", getUsername(persona.getAuthorId()));
        view.put("subscribeCount", persona.getSubscribeCount());
        view.put("subscribed", viewerId != null && isSubscribed(viewerId, persona.getId()));
        view.put("createdAt", persona.getCreatedAt() == null ? null : persona.getCreatedAt().getTime());
        // 列表页不返回完整 prompt（订阅后才可用），我的投稿返回自己可看
        if (viewerId != null && viewerId.equals(persona.getAuthorId())) {
            view.put("prompt", persona.getPrompt());
        } else {
            view.put("prompt", null);
        }
        return view;
    }

    private String getUsername(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String nickname = user.getNickname();
                    return nickname == null || nickname.trim().isEmpty()
                            ? user.getUsername() : nickname.trim();
                })
                .orElse("未知用户");
    }
}
