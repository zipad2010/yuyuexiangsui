package com.voice.service;

import com.voice.model.ForumPost;
import com.voice.model.ForumReply;
import com.voice.repository.ForumPostRepository;
import com.voice.repository.ForumReplyRepository;
import com.voice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class ForumService {
    
    @Autowired
    private ForumPostRepository forumPostRepository;
    
    @Autowired
    private ForumReplyRepository forumReplyRepository;

    @Autowired
    private UserRepository userRepository;
    
    public List<Map<String, Object>> getPosts(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        return forumPostRepository.findAllOrderByTime(pageRequest).stream()
                .map(this::toPostView)
                .collect(Collectors.toList());
    }
    
    public ForumPost getPost(Long postId) {
        return forumPostRepository.findById(postId).orElse(null);
    }
    
    public List<Map<String, Object>> getReplies(Long postId) {
        return forumReplyRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(this::toReplyView)
                .collect(Collectors.toList());
    }

    /**
     * 统计用户帖子在指定时间之后收到的新回复数（不含自己回复）
     */
    public long getNewReplyCount(Long userId, java.util.Date lastCheckedAt) {
        return forumReplyRepository.countNewRepliesForPostOwner(userId, lastCheckedAt);
    }

    /**
     * 最近回复过自己帖子的人（不含自己），按时间倒序，用于信息中心
     */
    public List<Map<String, Object>> getNewRepliesForPostOwner(Long userId, java.util.Date lastCheckedAt) {
        return forumReplyRepository.findNewRepliesForPostOwner(userId, lastCheckedAt).stream()
                .map(reply -> {
                    Map<String, Object> view = new HashMap<>();
                    view.put("replyId", reply.getId());
                    view.put("postId", reply.getPostId());
                    view.put("content", reply.getContent());
                    view.put("createdAt", reply.getCreatedAt() == null ? null : reply.getCreatedAt().getTime());
                    ForumPost post = forumPostRepository.findById(reply.getPostId()).orElse(null);
                    view.put("postTitle", post == null ? "帖子" : post.getTitle());
                    Map<String, Object> author = getUserBrief(reply.getUserId());
                    view.put("userId", reply.getUserId());
                    view.put("username", author.get("username"));
                    view.put("nickname", author.get("nickname"));
                    view.put("avatarUrl", author.get("avatarUrl"));
                    return view;
                })
                .collect(Collectors.toList());
    }
    
    @Transactional
    public ForumPost createPost(Long userId, String title, String content, String mediaUrls) {
        ForumPost post = new ForumPost();
        post.setUserId(userId);
        post.setTitle(title);
        post.setContent(content);
        post.setMediaUrls(mediaUrls == null || mediaUrls.trim().isEmpty() ? null : mediaUrls.trim());
        post.setLikeCount(0);
        post.setReplyCount(0);
        post.setStatus(1);
        return forumPostRepository.save(post);
    }

    @Transactional
    public ForumPost createPost(Long userId, String title, String content) {
        return createPost(userId, title, content, null);
    }
    
    @Transactional
    public ForumReply createReply(Long userId, Long postId, String content) {
        ForumReply reply = new ForumReply();
        reply.setUserId(userId);
        reply.setPostId(postId);
        reply.setContent(content);
        reply.setLikeCount(0);
        
        ForumPost post = forumPostRepository.findById(postId).orElse(null);
        if (post != null) {
            post.setReplyCount(post.getReplyCount() + 1);
            forumPostRepository.save(post);
        }
        
        return forumReplyRepository.save(reply);
    }

    public Map<String, Object> toPostView(ForumPost post) {
        if (post == null) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", post.getId());
        result.put("userId", post.getUserId());
        result.put("title", post.getTitle());
        result.put("content", post.getContent());
        result.put("mediaUrls", parseMediaUrls(post.getMediaUrls()));
        result.put("likeCount", post.getLikeCount());
        result.put("replyCount", post.getReplyCount());
        result.put("createdAt", post.getCreatedAt() == null ? null : post.getCreatedAt().getTime());
        Map<String, Object> author = getUserBrief(post.getUserId());
        result.put("username", author.get("username"));
        result.put("nickname", author.get("nickname"));
        result.put("avatarUrl", author.get("avatarUrl"));
        return result;
    }

    /** 解析媒体 JSON 数组字符串为 List<String>，容错处理非法格式 */
    private List<String> parseMediaUrls(String mediaUrls) {
        if (mediaUrls == null || mediaUrls.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            List<String> urls = new java.util.ArrayList<>();
            org.json.JSONArray array = new org.json.JSONArray(mediaUrls);
            for (int i = 0; i < array.length(); i++) {
                String url = array.optString(i, "");
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
            return urls;
        } catch (Exception e) {
            // 兜底：按逗号拆分
            String[] parts = mediaUrls.split(",");
            List<String> urls = new java.util.ArrayList<>();
            for (String part : parts) {
                if (part != null && !part.trim().isEmpty()) {
                    urls.add(part.trim());
                }
            }
            return urls;
        }
    }

    private Map<String, Object> toReplyView(ForumReply reply) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", reply.getId());
        result.put("postId", reply.getPostId());
        result.put("userId", reply.getUserId());
        result.put("content", reply.getContent());
        result.put("likeCount", reply.getLikeCount());
        result.put("createdAt", reply.getCreatedAt() == null ? null : reply.getCreatedAt().getTime());
        Map<String, Object> author = getUserBrief(reply.getUserId());
        result.put("username", author.get("username"));
        result.put("nickname", author.get("nickname"));
        result.put("avatarUrl", author.get("avatarUrl"));
        return result;
    }

    private Map<String, Object> getUserBrief(Long userId) {
        Map<String, Object> author = new HashMap<>();
        com.voice.model.User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            author.put("username", "未知用户");
            author.put("nickname", "未知用户");
            author.put("avatarUrl", "");
            return author;
        }
        String nickname = user.getNickname() == null || user.getNickname().trim().isEmpty()
                ? user.getUsername() : user.getNickname().trim();
        author.put("username", user.getUsername());
        author.put("nickname", nickname);
        author.put("avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
        return author;
    }
}