package com.voice.model;

import javax.persistence.*;
import java.util.Date;

/**
 * 人设订阅记录：用户订阅某个人设后才能使用
 */
@Entity
@Table(name = "persona_subscriptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "persona_id"})
})
public class PersonaSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "persona_id", nullable = false)
    private Long personaId;

    @Column(name = "created_at")
    private Date createdAt = new Date();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getPersonaId() { return personaId; }
    public void setPersonaId(Long personaId) { this.personaId = personaId; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
