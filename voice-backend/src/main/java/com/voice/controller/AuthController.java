package com.voice.controller;

import com.voice.model.ApiResponse;
import com.voice.model.LoginRequest;
import com.voice.model.User;
import com.voice.service.UserService;
import com.voice.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @PostMapping("/register")
    public ApiResponse<Map<String, String>> register(@RequestBody LoginRequest request) {
        try {
            User user = userService.register(request.getUsername(), request.getPassword());
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());
            
            Map<String, String> result = new HashMap<>();
            result.put("token", token);
            result.put("userId", user.getId().toString());
            result.put("username", user.getUsername());
            result.put("balance", user.getPoints().toString());
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody LoginRequest request) {
        User user = userService.login(request.getUsername(), request.getPassword());
        if (user == null) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId().toString());
        result.put("username", user.getUsername());
        result.put("balance", user.getPoints().toString());
        
        return ApiResponse.success(result);
    }
}