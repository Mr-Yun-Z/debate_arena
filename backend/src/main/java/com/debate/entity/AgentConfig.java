package com.debate.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_config")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色标识: PRO, CON, JUDGE */
    @Column(nullable = false, unique = true, length = 10)
    private String role;

    /** 角色名称 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 人设描述 */
    @Column(nullable = false, length = 200)
    private String persona;

    /** 系统提示词 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    /** 头像颜色 */
    @Column(length = 20)
    private String color;

    /** 头像图标(emoji) */
    @Column(length = 10)
    private String icon;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
