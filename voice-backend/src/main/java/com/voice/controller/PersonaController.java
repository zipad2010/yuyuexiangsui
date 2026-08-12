package com.voice.controller;

import com.voice.config.JwtUtil;
import com.voice.model.ApiResponse;
import com.voice.model.Persona;
import com.voice.service.PersonaService;
import com.voice.service.ContentReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 人设投稿中心接口
 */
@RestController
@RequestMapping("/api/personas")
@CrossOrigin(origins = "*")
public class PersonaController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PersonaService personaService;

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

    /** 人设广场：分页浏览全部人设 */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        return ApiResponse.success(personaService.listPersonas(page, size, userId));
    }

    /** 我的投稿 */
    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(personaService.listMyPersonas(userId));
    }

    /** 我订阅的人设（用于创建/切换对话时选择） */
    @GetMapping("/subscribed")
    public ApiResponse<List<Map<String, Object>>> subscribed(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(personaService.listSubscribedPersonas(userId));
    }

    /** 发布人设 */
    @PostMapping("/publish")
    public ApiResponse<Persona> publish(@RequestBody Map<String, String> request,
                                        HttpServletRequest httpRequest) {
        Long userId = getUserIdFromToken(httpRequest);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        String name = request.get("name");
        String description = request.get("description");
        String prompt = request.get("prompt");
        // 内容审查：人设内容涉黄/涉政拒绝发布并记录违规
        String reviewText = (name == null ? "" : name) + "\n"
                + (description == null ? "" : description) + "\n"
                + (prompt == null ? "" : prompt);
        ContentReviewService.ReviewResult review = contentReviewService.review(reviewText);
        if (!review.pass) {
            contentReviewService.recordViolation(userId, "persona", review.reason, reviewText);
            return ApiResponse.error(403, "人设内容不符合规范（" + review.reason + "），已警告并记录，请修改后重试");
        }
        try {
            Persona persona = personaService.createPersona(userId, name, description, prompt);
            return ApiResponse.success(persona);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 订阅人设 */
    @PostMapping("/{personaId}/subscribe")
    public ApiResponse<String> subscribe(@PathVariable Long personaId, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        if (!personaService.subscribe(userId, personaId)) {
            return ApiResponse.error(404, "人设不存在");
        }
        return ApiResponse.success("订阅成功");
    }

    /** 取消订阅 */
    @PostMapping("/{personaId}/unsubscribe")
    public ApiResponse<String> unsubscribe(@PathVariable Long personaId, HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        personaService.unsubscribe(userId, personaId);
        return ApiResponse.success("已取消订阅");
    }
}
