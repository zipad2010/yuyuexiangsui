package com.voice.service;

import com.voice.model.User;
import com.voice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Autowired
    private BalanceService balanceService;
    
    @Value("${app.upload.avatar-path:/www/wwwroot/voice-backend/uploads/avatars/}")
    private String avatarUploadPath;

    @Value("${app.upload.avatar-url-base}")
    private String avatarUrlBase;

    @Value("${app.upload.wallpaper-path:/www/wwwroot/voice-backend/uploads/wallpapers/}")
    private String wallpaperUploadPath;

    @Value("${app.upload.wallpaper-url-base:${app.upload.avatar-url-base}}")
    private String wallpaperUrlBase;
    
    public Map<String, Object> getProfile(Long userId) {
        Map<String, Object> profile = new HashMap<>();
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        
        profile.put("userId", user.getId());
        profile.put("username", user.getUsername());
        profile.put("points", balanceService.getPoints(userId));
        profile.put("totalUsage", user.getTotalUsage());
        profile.put("status", user.getStatus());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("isSponsor", user.getIsSponsor() == 1);
        profile.put("nickname", user.getNickname() == null ? user.getUsername() : user.getNickname());
        profile.put("signature", user.getSignature() == null ? "" : user.getSignature());
        profile.put("avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
        profile.put("wallpaperUrl", user.getWallpaperUrl() == null ? "" : user.getWallpaperUrl());
        
        return profile;
    }
    
    @Transactional
    public void updateProfile(Long userId, String nickname, String signature, Integer gender) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        String normalizedNickname = nickname == null ? "" : nickname.trim();
        String normalizedSignature = signature == null ? "" : signature.trim();
        if (normalizedNickname.isEmpty()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        if (normalizedNickname.length() > 32) {
            throw new IllegalArgumentException("昵称不能超过 32 个字符");
        }
        if (normalizedSignature.length() > 120) {
            throw new IllegalArgumentException("个性签名不能超过 120 个字符");
        }
        user.setNickname(normalizedNickname);
        user.setSignature(normalizedSignature);
        user.setUpdatedAt(new java.util.Date());
        userRepository.save(user);
    }

    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("头像不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片文件");
        }
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            File dir = new File(avatarUploadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建头像目录");
            }
            
            String fileName = userId + "_" + UUID.randomUUID().toString() + ".jpg";
            File destFile = new File(dir, fileName);
            file.transferTo(destFile);

            String baseUrl = avatarUrlBase.endsWith("/") ? avatarUrlBase : avatarUrlBase + "/";
            String avatarUrl = baseUrl + fileName;
            user.setAvatarUrl(avatarUrl);
            user.setUpdatedAt(new java.util.Date());
            userRepository.save(user);
            return avatarUrl;
        } catch (IOException e) {
            throw new RuntimeException("上传失败", e);
        }
    }

    @Transactional
    public String uploadWallpaper(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择壁纸图片");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("壁纸不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片文件");
        }
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            File dir = new File(wallpaperUploadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建壁纸目录");
            }
            String fileName = userId + "_" + UUID.randomUUID().toString() + ".jpg";
            file.transferTo(new File(dir, fileName));

            String baseUrl = wallpaperUrlBase.endsWith("/") ? wallpaperUrlBase : wallpaperUrlBase + "/";
            String wallpaperUrl = baseUrl + fileName;
            user.setWallpaperUrl(wallpaperUrl);
            user.setUpdatedAt(new java.util.Date());
            userRepository.save(user);
            return wallpaperUrl;
        } catch (IOException e) {
            throw new RuntimeException("上传失败", e);
        }
    }
}