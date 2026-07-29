package com.voice.model;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    private String email;

    @Column(length = 64)
    private String nickname;

    @Column(length = 255)
    private String signature;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(precision = 10, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    private Integer points;

    @Column(name = "points_migrated")
    private Boolean pointsMigrated = false;

    @Column(name = "total_usage", precision = 10, scale = 4)
    private BigDecimal totalUsage = BigDecimal.ZERO;

    private Integer status = 1;

    @Column(name = "created_at")
    private Date createdAt = new Date();

    @Column(name = "updated_at")
    private Date updatedAt = new Date();

    @Column(name = "is_sponsor")
    private Integer isSponsor = 0;

    // ========== Getter and Setter ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public Boolean getPointsMigrated() {
        return pointsMigrated;
    }

    public void setPointsMigrated(Boolean pointsMigrated) {
        this.pointsMigrated = pointsMigrated;
    }

    public BigDecimal getTotalUsage() {
        return totalUsage;
    }

    public void setTotalUsage(BigDecimal totalUsage) {
        this.totalUsage = totalUsage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getIsSponsor() {
        return isSponsor;
    }

    public void setIsSponsor(Integer isSponsor) {
        this.isSponsor = isSponsor;
    }
}