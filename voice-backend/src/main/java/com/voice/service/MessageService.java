package com.voice.service;

import com.voice.model.PrivateMessage;
import com.voice.repository.PrivateMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class MessageService {

    @Autowired
    private PrivateMessageRepository privateMessageRepository;

    /**
     * 获取用户的所有私信会话（包括发送和接收的）
     */
    public List<PrivateMessage> getConversations(Long userId) {
        return privateMessageRepository.findByUserId(userId);
    }

    /**
     * 获取两个用户之间的完整对话
     */
    public List<PrivateMessage> getConversation(Long userId, Long targetUserId) {
        return privateMessageRepository.findConversation(userId, targetUserId);
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
}