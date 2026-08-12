package com.voice.controller;

import com.voice.config.JwtUtil;
import com.voice.model.ApiResponse;
import com.voice.model.Conversation;
import com.voice.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 对话会话管理接口：创建 / 列表 / 重命名 / 删除 / 设置人设
 */
@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "*")
public class ConversationController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ConversationService conversationService;

    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    /** 会话列表（按最近活动倒序） */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(conversationService.listConversations(userId));
    }

    /** 创建会话 */
    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, String> request,
                                                   HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        String title = request.get("title");
        Long personaId = null;
        try {
            String personaIdStr = request.get("personaId");
            if (personaIdStr != null && !personaIdStr.trim().isEmpty()) {
                personaId = Long.parseLong(personaIdStr);
            }
        } catch (RuntimeException ignored) {
        }
        Conversation conversation = conversationService.createConversation(userId, title);
        if (personaId != null && personaId > 0) {
            conversation = conversationService.setConversationPersona(userId, conversation.getId(), personaId);
        }
        return ApiResponse.success(conversationService.getConversationView(userId, conversation.getId()));
    }

    /** 重命名会话 */
    @PostMapping("/{conversationId}/rename")
    public ApiResponse<Map<String, Object>> rename(@PathVariable Long conversationId,
                                                   @RequestBody Map<String, String> request,
                                                   HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Conversation conversation = conversationService.renameConversation(
                userId, conversationId, request.get("title"));
        if (conversation == null) {
            return ApiResponse.error(404, "会话不存在");
        }
        return ApiResponse.success(conversationService.getConversationView(userId, conversationId));
    }

    /** 为会话设置人设 */
    @PostMapping("/{conversationId}/persona")
    public ApiResponse<Map<String, Object>> setPersona(@PathVariable Long conversationId,
                                                       @RequestBody Map<String, String> request,
                                                       HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Long personaId = null;
        try {
            String personaIdStr = request.get("personaId");
            if (personaIdStr != null && !personaIdStr.trim().isEmpty()) {
                personaId = Long.parseLong(personaIdStr);
            }
        } catch (RuntimeException ignored) {
        }
        Conversation conversation = conversationService.setConversationPersona(
                userId, conversationId, personaId);
        if (conversation == null) {
            return ApiResponse.error(404, "会话不存在");
        }
        return ApiResponse.success(conversationService.getConversationView(userId, conversationId));
    }

    /** 删除会话 */
    @PostMapping("/{conversationId}/delete")
    public ApiResponse<String> delete(@PathVariable Long conversationId, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (!conversationService.deleteConversation(userId, conversationId)) {
            return ApiResponse.error(404, "会话不存在");
        }
        return ApiResponse.success("已删除");
    }
}
