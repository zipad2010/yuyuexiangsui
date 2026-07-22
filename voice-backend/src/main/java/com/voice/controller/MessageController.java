package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.model.PrivateMessage;
import com.voice.service.MessageService;
import com.voice.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
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
    
    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
    
    @GetMapping("/conversations")
    public ApiResponse<List<PrivateMessage>> getConversations(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(messageService.getConversations(userId));
    }
    
    @GetMapping("/conversation/{targetUserId}")
    public ApiResponse<List<PrivateMessage>> getConversation(
            @PathVariable Long targetUserId,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(messageService.getConversation(userId, targetUserId));
    }
    
    @PostMapping("/send")
    public ApiResponse<PrivateMessage> sendMessage(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Long toUserId = Long.parseLong(request.get("toUserId"));
        String content = request.get("content");
        if (content == null || content.isEmpty()) {
            return ApiResponse.error(400, "消息内容不能为空");
        }
        return ApiResponse.success(messageService.sendMessage(userId, toUserId, content));
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
}