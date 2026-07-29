package com.voice.controller;

import com.voice.config.JwtUtil;
import com.voice.model.ApiResponse;
import com.voice.model.User;
import com.voice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = "*")
public class ModelController {
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserService userService;

    @Value("${api.deepseek.model:deepseek-v4-pro}")
    private String chatModel;

    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return ApiResponse.error(401, "未登录");
        }
        Long userId = jwtUtil.getUserIdFromToken(header.substring(7));
        User user = userService.findById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getIsSponsor())) {
            return ApiResponse.error(403, "该功能仅对赞助者开放");
        }

        List<Map<String, Object>> models = new ArrayList<>();
        models.add(model(chatModel, "DeepSeek V4 Pro", false));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("models", models);
        return ApiResponse.success(result);
    }

    private Map<String, Object> model(String id, String name, boolean supportsThinking) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("supportsThinking", supportsThinking);
        return item;
    }
}