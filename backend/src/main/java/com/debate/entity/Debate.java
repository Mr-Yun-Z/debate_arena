package com.debate.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "debate")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Debate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 辩论议题 */
    @Column(nullable = false, length = 500)
    private String topic;

    /** 状态: PENDING, RUNNING, FINISHED, TERMINATED */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DebateStatus status;

    /** 当前轮次 (从1开始) */
    @Column(nullable = false)
    private Integer currentRound;

    /** 当前发言角色: PRO, CON, JUDGE */
    @Column(length = 10)
    private String currentSpeaker;

    /** 预设最大轮数 */
    @Column(nullable = false)
    private Integer maxRounds;

    /** 正方胜利论点数 */
    private Integer proScore;

    /** 反方胜利论点数 */
    private Integer conScore;

    /** 胜负结果 */
    @Column(length = 50)
    private String result;

    /** 裁判总结报告 */
    @Column(columnDefinition = "TEXT")
    private String judgeReport;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "debate", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<DebateMessage> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = DebateStatus.PENDING;
        if (this.currentRound == null) this.currentRound = 0;
    }

    public enum DebateStatus {
        PENDING, RUNNING, FINISHED, TERMINATED
    }
}
