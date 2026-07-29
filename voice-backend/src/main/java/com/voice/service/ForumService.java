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
    
    @Transactional
    public ForumPost createPost(Long userId, String title, String content) {
        ForumPost post = new ForumPost();
        post.setUserId(userId);
        post.setTitle(title);
        post.setContent(content);
        post.setLikeCount(0);
        post.setReplyCount(0);
        post.setStatus(1);
        return forumPostRepository.save(post);
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
        result.put("likeCount", post.getLikeCount());
        result.put("replyCount", post.getReplyCount());
        result.put("createdAt", post.getCreatedAt() == null ? null : post.getCreatedAt().getTime());
        result.put("username", getUsername(post.getUserId()));
        result.put("nickname", getUsername(post.getUserId()));
        return result;
    }

    private Map<String, Object> toReplyView(ForumReply reply) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", reply.getId());
        result.put("postId", reply.getPostId());
        result.put("userId", reply.getUserId());
        result.put("content", reply.getContent());
        result.put("likeCount", reply.getLikeCount());
        result.put("createdAt", reply.getCreatedAt() == null ? null : reply.getCreatedAt().getTime());
        result.put("username", getUsername(reply.getUserId()));
        result.put("nickname", getUsername(reply.getUserId()));
        return result;
    }

    private String getUsername(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getUsername())
                .orElse("未知用户");
    }
}