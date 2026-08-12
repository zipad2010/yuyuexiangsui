package com.voice.model;

import javax.persistence.*;
import java.util.Date;

/**
 * 违规记录：内容审查拦截的违规内容，供管理员在管理中心查看
 */
@Entity
@Table(name = "violation_record")
public class ViolationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 违规用户 id */
    @Column(name = "user_id")
    private Long userId;

    /** 违规用户用户名 */
    private String username;

    /** 违规来源：forum / chat / persona */
    private String source;

    /** 违规原因（模型判定，如：涉黄 / 涉政 / 正常） */
    @Column(name = "violation_reason")
    private String violationReason;

    /** 违规原文 */
    @Column(length = 2000)
    private String content;

    /** 处理状态：0 待处理 / 1 已处理 */
    private Integer status = 0;

    @Column(name = "created_at")
    private Date createdAt = new Date();

    // ========== Getter and Setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getViolationReason() { return violationReason; }
    public void setViolationReason(String violationReason) { this.violationReason = violationReason; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
