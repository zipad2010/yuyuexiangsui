package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.model.PrivateMessage;
import com.voice.service.MessageService;
import com.voice.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "*")
public class MessageController {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private MessageService messageService;

    @Autowired
    private com.voice.service.ForumService forumService;
    
    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
    
    @GetMapping("/conversations")
    public ApiResponse<List<Map<String, Object>>> getConversations(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(messageService.getConversations(userId));
    }
    
    @GetMapping("/conversation/{targetUserId}")
    public ApiResponse<List<Map<String, Object>>> getConversation(
            @PathVariable Long targetUserId,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(messageService.getConversation(userId, targetUserId));
    }

    @GetMapping("/recipient")
    public ApiResponse<Map<String, Object>> findRecipient(
            @RequestParam String username,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isEmpty()) {
            return ApiResponse.error(400, "请输入用户名");
        }
        Map<String, Object> recipient = messageService.findRecipient(normalizedUsername);
        if (recipient == null) {
            return ApiResponse.error(404, "未找到该用户");
        }
        if (userId.equals(recipient.get("userId"))) {
            return ApiResponse.error(400, "不能给自己发送私信");
        }
        return ApiResponse.success(recipient);
    }
    
    @PostMapping("/send")
    public ApiResponse<PrivateMessage> sendMessage(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Long toUserId;
        try {
            toUserId = Long.parseLong(request.get("toUserId"));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, "收件人无效");
        }
        if (userId.equals(toUserId)) {
            return ApiResponse.error(400, "不能给自己发送私信");
        }
        if (!messageService.recipientExists(toUserId)) {
            return ApiResponse.error(404, "收件人不存在");
        }
        String content = request.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ApiResponse.error(400, "消息内容不能为空");
        }
        return ApiResponse.success(messageService.sendMessage(userId, toUserId, content.trim()));
    }
    
    @PostMapping("/mark-read")
    public ApiResponse<String> markAsRead(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        messageService.markAsRead(userId);
        return ApiResponse.success("已读");
    }

    @GetMapping("/notifications/summary")
    public ApiResponse<Map<String, Object>> getNotificationSummary(
            @RequestParam(required = false) Long forumCheckedAt,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("privateMessageUnread", messageService.getUnreadCount(userId));
        summary.put("forumReplyUnread", forumService.getNewReplyCount(userId,
                new Date(forumCheckedAt == null ? 0L : forumCheckedAt)));
        return ApiResponse.success(summary);
    }
}