package com.voice.model;

import javax.persistence.*;
import java.util.Date;

/**
 * 人设投稿：用户上传的角色设定，其他用户订阅后可用
 */
@Entity
@Table(name = "personas")
public class Persona {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "subscribe_count")
    private Integer subscribeCount = 0;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(name = "created_at")
    private Date createdAt = new Date();

    // ========== Getter and Setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public Integer getSubscribeCount() { return subscribeCount; }
    public void setSubscribeCount(Integer subscribeCount) { this.subscribeCount = subscribeCount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
