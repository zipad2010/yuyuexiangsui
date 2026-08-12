package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.model.ForumPost;
import com.voice.model.ForumReply;
import com.voice.service.ForumService;
import com.voice.service.ContentReviewService;
import com.voice.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    @Autowired
    private com.voice.service.ForumMediaService forumMediaService;

    @Autowired
    private ContentReviewService contentReviewService;
    
    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
    
    @GetMapping("/posts")
    public ApiResponse<List<Map<String, Object>>> getPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(forumService.getPosts(page, size));
    }
    
    @GetMapping("/post/{postId}")
    public ApiResponse<Map<String, Object>> getPostDetail(@PathVariable Long postId) {
        Map<String, Object> result = new HashMap<>();
        result.put("post", forumService.toPostView(forumService.getPost(postId)));
        result.put("replies", forumService.getReplies(postId));
        return ApiResponse.success(result);
    }
    
    @PostMapping("/post")
    public ApiResponse<ForumPost> createPost(
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        String title = request.get("title") == null ? null : request.get("title").toString();
        String content = request.get("content") == null ? null : request.get("content").toString();
        if (title == null || title.isEmpty() || content == null || content.isEmpty()) {
            return ApiResponse.error(400, "标题和内容不能为空");
        }
        // 内容审查：涉黄/涉政内容拒绝发布并记录违规
        String reviewText = title + "\n" + content;
        ContentReviewService.ReviewResult review = contentReviewService.review(reviewText);
        if (!review.pass) {
            contentReviewService.recordViolation(userId, "forum", review.reason, reviewText);
            return ApiResponse.error(403, "内容不符合规范（" + review.reason + "），已警告并记录，请修改后重试");
        }
        // mediaUrls: 可传 JSON 数组字符串或逗号分隔的相对 URL 列表
        String mediaUrls = null;
        Object mediaObj = request.get("mediaUrls");
        if (mediaObj instanceof java.util.List) {
            mediaUrls = new org.json.JSONArray((java.util.List<?>) mediaObj).toString();
        } else if (mediaObj != null) {
            mediaUrls = mediaObj.toString();
        }
        return ApiResponse.success(forumService.createPost(userId, title, content, mediaUrls));
    }

    /** 上传论坛媒体（图片/视频），返回相对 URL */
    @PostMapping("/upload")
    public ApiResponse<Map<String, String>> uploadMedia(
            @RequestParam("media") MultipartFile file,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        try {
            String url = forumMediaService.uploadMedia(file);
            Map<String, String> result = new HashMap<>();
            result.put("url", url);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (RuntimeException e) {
            return ApiResponse.error(500, e.getMessage());
        }
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