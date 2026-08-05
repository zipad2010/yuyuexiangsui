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
                String username = getUsername(targetUserId);
                conversation.put("userId", targetUserId);
                conversation.put("username", username);
                conversation.put("nickname", username);
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
                .map(user -> {
                    Map<String, Object> recipient = new LinkedHashMap<>();
                    recipient.put("userId", user.getId());
                    recipient.put("username", user.getUsername());
                    return recipient;
                })
                .orElse(null);
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