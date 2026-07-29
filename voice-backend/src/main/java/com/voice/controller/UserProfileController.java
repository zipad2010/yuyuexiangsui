package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.service.UserProfileService;
import com.voice.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserProfileController {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserProfileService userProfileService;
    
    private Long getUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
    
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile(HttpServletRequest request) {
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        try {
            return ApiResponse.success(userProfileService.getProfile(userId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }
    
    @PostMapping("/profile")
    public ApiResponse<Map<String, String>> updateProfile(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        
        String nickname = requestBody.get("nickname");
        String signature = requestBody.get("signature");
        Integer gender = null;
        if (requestBody.containsKey("gender")) {
            gender = Integer.parseInt(requestBody.get("gender"));
        }
        
        try {
            userProfileService.updateProfile(userId, nickname, signature, gender);
            Map<String, String> result = new HashMap<>();
            result.put("message", "更新成功");
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    @PostMapping("/avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(
            @RequestParam("avatar") MultipartFile file,
            HttpServletRequest request) {
        
        Long userId = getUserIdFromToken(request);
        if (userId == null) {
            return ApiResponse.error(401, "未登录");
        }
        
        try {
            String avatarUrl = userProfileService.uploadAvatar(userId, file);
            Map<String, String> result = new HashMap<>();
            result.put("avatarUrl", avatarUrl);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (RuntimeException e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }
}