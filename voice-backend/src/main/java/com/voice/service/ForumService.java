package com.voice.service;

import com.voice.model.ForumPost;
import com.voice.model.ForumReply;
import com.voice.repository.ForumPostRepository;
import com.voice.repository.ForumReplyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ForumService {
    
    @Autowired
    private ForumPostRepository forumPostRepository;
    
    @Autowired
    private ForumReplyRepository forumReplyRepository;
    
    public List<ForumPost> getPosts(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page - 1, size);
        return forumPostRepository.findAllOrderByTime(pageRequest);
    }
    
    public ForumPost getPost(Long postId) {
        return forumPostRepository.findById(postId).orElse(null);
    }
    
    public List<ForumReply> getReplies(Long postId) {
        return forumReplyRepository.findByPostIdOrderByCreatedAtAsc(postId);
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
}