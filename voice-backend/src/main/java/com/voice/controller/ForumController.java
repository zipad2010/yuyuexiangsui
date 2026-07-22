package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.model.ForumPost;
import com.voice.model.ForumReply;
import com.voice.service.ForumService;
import com.voice.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/forum")
@CrossOrigin(origins = "*")
public class ForumController {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private ForumService forumService;
    
    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
    
    @GetMapping("/posts")
    public ApiResponse<List<ForumPost>> getPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(forumService.getPosts(page, size));
    }
    
    @GetMapping("/post/{postId}")
    public ApiResponse<Map<String, Object>> getPostDetail(@PathVariable Long postId) {
        Map<String, Object> result = new HashMap<>();
        result.put("post", forumService.getPost(postId));
        result.put("replies", forumService.getReplies(postId));
        return ApiResponse.success(result);
    }
    
    @PostMapping("/post")
    public ApiResponse<ForumPost> createPost(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        String title = request.get("title");
        String content = request.get("content");
        if (title == null || title.isEmpty() || content == null || content.isEmpty()) {
            return ApiResponse.error(400, "标题和内容不能为空");
        }
        return ApiResponse.success(forumService.createPost(userId, title, content));
    }
    
    @PostMapping("/reply")
    public ApiResponse<ForumReply> createReply(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        Long postId = Long.parseLong(request.get("postId"));
        String content = request.get("content");
        if (content == null || content.isEmpty()) {
            return ApiResponse.error(400, "回复内容不能为空");
        }
        return ApiResponse.success(forumService.createReply(userId, postId, content));
    }
}