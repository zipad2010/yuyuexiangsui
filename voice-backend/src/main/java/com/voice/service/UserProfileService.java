package com.voice.service;

import com.voice.model.User;
import com.voice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class UserProfileService {
    
    @Autowired
    private UserRepository userRepository;
    
    private static final String AVATAR_UPLOAD_PATH = "/www/wwwroot/voice-backend/uploads/avatars/";
    private static final String AVATAR_URL_BASE = "http://你的服务器IP:8080/uploads/avatars/";
    
    public Map<String, Object> getProfile(Long userId) {
        Map<String, Object> profile = new HashMap<>();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return profile;
        
        profile.put("userId", user.getId());
        profile.put("username", user.getUsername());
        profile.put("balance", user.getBalance());
        profile.put("totalUsage", user.getTotalUsage());
        profile.put("status", user.getStatus());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("isSponsor", user.getIsSponsor() == 1);
        
        return profile;
    }
    
    public void updateProfile(Long userId, String nickname, String signature, Integer gender) {
        // 扩展用户资料
    }
    
    public String uploadAvatar(Long userId, MultipartFile file) {
        try {
            File dir = new File(AVATAR_UPLOAD_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String fileName = userId + "_" + UUID.randomUUID().toString() + ".jpg";
            File destFile = new File(dir, fileName);
            file.transferTo(destFile);
            
            return AVATAR_URL_BASE + fileName;
        } catch (IOException e) {
            throw new RuntimeException("上传失败", e);
        }
    }
}