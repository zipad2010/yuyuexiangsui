package com.voice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 论坛图片/视频上传服务
 */
@Service
public class ForumMediaService {

    @Value("${app.upload.post-media-path:/www/wwwroot/voice-backend/uploads/posts/}")
    private String postMediaUploadPath;

    @Value("${app.upload.post-media-url-base:${app.upload.avatar-url-base:/uploads/}}")
    private String postMediaUrlBase;

    /**
     * 上传论坛媒体文件（图片/视频），返回相对 URL（如 /uploads/posts/xxx.jpg）
     */
    public String uploadMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择媒体文件");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new IllegalArgumentException("媒体文件不能超过 50MB");
        }
        String contentType = file.getContentType();
        if (contentType == null
                || (!contentType.startsWith("image/") && !contentType.startsWith("video/"))) {
            throw new IllegalArgumentException("仅支持图片或视频文件");
        }
        try {
            File dir = new File(postMediaUploadPath);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("无法创建媒体目录");
            }
            String extension = resolveExtension(file.getOriginalFilename(), contentType);
            String fileName = UUID.randomUUID().toString() + extension;
            File destFile = new File(dir, fileName);
            file.transferTo(destFile);

            String baseUrl = postMediaUrlBase.endsWith("/")
                    ? postMediaUrlBase : postMediaUrlBase + "/";
            return baseUrl + fileName;
        } catch (IOException e) {
            throw new RuntimeException("上传失败", e);
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String ext = originalFilename.substring(dot);
                if (ext.matches("\\.[A-Za-z0-9]{1,8}")) {
                    return ext.toLowerCase();
                }
            }
        }
        if (contentType != null && contentType.startsWith("video/")) {
            return ".mp4";
        }
        return ".jpg";
    }
}
