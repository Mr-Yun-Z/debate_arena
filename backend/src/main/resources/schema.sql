-- ============================================
-- AI 多智能体辩论台 - 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS debate_arena
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE debate_arena;

-- 辩论主表
CREATE TABLE IF NOT EXISTS debate (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic           VARCHAR(500) NOT NULL COMMENT '辩论议题',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/FINISHED/TERMINATED',
    current_round   INT          NOT NULL DEFAULT 0 COMMENT '当前轮次(从1开始)',
    current_speaker VARCHAR(10)  DEFAULT NULL COMMENT '当前发言角色: PRO/CON/JUDGE',
    max_rounds      INT          NOT NULL DEFAULT 5 COMMENT '预设最大轮数',
    pro_score       INT          DEFAULT NULL COMMENT '正方得分',
    con_score       INT          DEFAULT NULL COMMENT '反方得分',
    result          VARCHAR(50)  DEFAULT NULL COMMENT '胜负结果',
    judge_report    TEXT         DEFAULT NULL COMMENT '裁判总结报告',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finished_at     DATETIME     DEFAULT NULL COMMENT '结束时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辩论主表';

-- 辩论消息表
CREATE TABLE IF NOT EXISTS debate_message (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    debate_id  BIGINT       NOT NULL COMMENT '关联辩论ID',
    role       VARCHAR(10)  NOT NULL COMMENT '发言角色: PRO/CON/JUDGE',
    round      INT          NOT NULL COMMENT '发言轮次',
    content    TEXT         NOT NULL COMMENT '发言内容',
    sequence   INT          NOT NULL COMMENT '发言顺序号',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发言时间',
    FOREIGN KEY (debate_id) REFERENCES debate(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辩论消息表';

CREATE INDEX idx_message_debate_seq ON debate_message(debate_id, sequence);

-- Agent配置表
CREATE TABLE IF NOT EXISTS agent_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    role          VARCHAR(10)  NOT NULL UNIQUE COMMENT '角色标识: PRO/CON/JUDGE',
    name          VARCHAR(50)  NOT NULL COMMENT '角色名称',
    persona       VARCHAR(200) NOT NULL COMMENT '人设描述',
    system_prompt TEXT         NOT NULL COMMENT '系统提示词',
    color         VARCHAR(20)  DEFAULT NULL COMMENT '头像颜色',
    icon          VARCHAR(10)  DEFAULT NULL COMMENT '头像图标(emoji)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent配置表';

-- Agent初始数据由 DataInitializer.java 在应用启动时自动插入（含完整System Prompt）
-- 如需手动插入，可参考 DataInitializer 中 buildProPrompt/buildConPrompt/buildJudgePrompt 的内容
