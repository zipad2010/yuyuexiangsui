package com.voice.service;

import com.voice.model.PrivateMessage;
import com.voice.repository.PrivateMessageRepository;
import com.voice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MessageService {

    @Autowired
    private PrivateMessageRepository privateMessageRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 获取用户的所有私信会话（包括发送和接收的）
     */
    public List<Map<String, Object>> getConversations(Long userId) {
        Map<Long, Map<String, Object>> conversations = new LinkedHashMap<>();
        for (PrivateMessage message : privateMessageRepository.findByUserId(userId)) {
            Long targetUserId = userId.equals(message.getFromUserId())
                    ? message.getToUserId() : message.getFromUserId();
            if (!conversations.containsKey(targetUserId)) {
                Map<String, Object> conversation = new LinkedHashMap<>();
                Map<String, Object> peer = getUserBrief(targetUserId);
                conversation.put("userId", targetUserId);
                conversation.put("username", peer.get("username"));
                conversation.put("nickname", peer.get("nickname"));
                conversation.put("avatarUrl", peer.get("avatarUrl"));
                conversation.put("lastMessage", message.getContent());
                conversation.put("lastMessageTime", message.getCreatedAt() == null
                        ? null : message.getCreatedAt().getTime());
                conversations.put(targetUserId, conversation);
            }
        }
        return new ArrayList<>(conversations.values());
    }

    /**
     * 获取两个用户之间的完整对话
     */
    public List<Map<String, Object>> getConversation(Long userId, Long targetUserId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PrivateMessage message : privateMessageRepository.findConversation(userId, targetUserId)) {
            result.add(toMessageView(message));
        }
        return result;
    }

    public Map<String, Object> findRecipient(String username) {
        return userRepository.findByUsername(username)
                .map(user -> getUserBrief(user.getId()))
                .orElse(null);
    }

    /**
     * 最近收到的未读私信（含发送者昵称、头像），按时间倒序，用于信息中心
     */
    public List<Map<String, Object>> getRecentUnreadMessages(Long userId, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PrivateMessage message : privateMessageRepository.findByUserId(userId)) {
            // 只统计别人发给我的未读消息
            if (!userId.equals(message.getToUserId()) || Integer.valueOf(1).equals(message.getIsRead())) {
                continue;
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("fromUserId", message.getFromUserId());
            Map<String, Object> sender = getUserBrief(message.getFromUserId());
            view.put("username", sender.get("username"));
            view.put("nickname", sender.get("nickname"));
            view.put("avatarUrl", sender.get("avatarUrl"));
            view.put("content", message.getContent());
            view.put("createdAt", message.getCreatedAt() == null ? null : message.getCreatedAt().getTime());
            result.add(view);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /**
     * 按用户名/昵称搜索用户（排除自己），用于发起私信时的用户列表
     */
    public List<Map<String, Object>> searchUsers(Long currentUserId, String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        java.util.List<com.voice.model.User> users = normalized.isEmpty()
                ? userRepository.findAll()
                : userRepository.searchByKeyword(normalized);
        List<Map<String, Object>> result = new ArrayList<>();
        for (com.voice.model.User user : users) {
            if (currentUserId != null && currentUserId.equals(user.getId())) {
                continue;
            }
            result.add(getUserBrief(user.getId()));
        }
        return result;
    }

    private Map<String, Object> getUserBrief(Long userId) {
        Map<String, Object> brief = new LinkedHashMap<>();
        com.voice.model.User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            brief.put("userId", userId);
            brief.put("username", "未知用户");
            brief.put("nickname", "未知用户");
            brief.put("avatarUrl", "");
            return brief;
        }
        String nickname = user.getNickname() == null || user.getNickname().trim().isEmpty()
                ? user.getUsername() : user.getNickname().trim();
        brief.put("userId", user.getId());
        brief.put("username", user.getUsername());
        brief.put("nickname", nickname);
        brief.put("avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
        return brief;
    }

    public boolean recipientExists(Long userId) {
        return userId != null && userRepository.existsById(userId);
    }

    public long getUnreadCount(Long userId) {
        return privateMessageRepository.countUnreadByUserId(userId);
    }

    /**
     * 发送私信
     */
    @Transactional
    public PrivateMessage sendMessage(Long fromUserId, Long toUserId, String content) {
        PrivateMessage message = new PrivateMessage();
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setContent(content);
        message.setIsRead(0);
        return privateMessageRepository.save(message);
    }

    /**
     * 标记所有消息为已读
     */
    @Transactional
    public void markAsRead(Long userId) {
        privateMessageRepository.markAsRead(userId);
    }

    private Map<String, Object> toMessageView(PrivateMessage message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", message.getId());
        result.put("fromUserId", message.getFromUserId());
        result.put("toUserId", message.getToUserId());
        result.put("fromUsername", getUsername(message.getFromUserId()));
        result.put("toUsername", getUsername(message.getToUserId()));
        result.put("content", message.getContent());
        result.put("isRead", message.getIsRead());
        result.put("createdAt", message.getCreatedAt() == null ? null : message.getCreatedAt().getTime());
        return result;
    }

    private String getUsername(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getUsername())
                .orElse("未知用户");
    }
}