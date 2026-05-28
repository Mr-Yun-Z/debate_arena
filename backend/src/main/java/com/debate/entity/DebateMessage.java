package com.debate.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "debate_message")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DebateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debate_id", nullable = false)
    private Debate debate;

    /** 发言角色: PRO, CON, JUDGE */
    @Column(nullable = false, length = 10)
    private String role;

    /** 发言轮次 */
    @Column(nullable = false)
    private Integer round;

    /** 发言内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 发言顺序号 */
    @Column(nullable = false)
    private Integer sequence;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
