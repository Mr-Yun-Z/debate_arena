package com.debate.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class DebateDTOs {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateDebateRequest {
        private String topic;
        private Integer maxRounds;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DebateResponse {
        private Long id;
        private String topic;
        private String status;
        private Integer currentRound;
        private String currentSpeaker;
        private Integer maxRounds;
        private Integer proScore;
        private Integer conScore;
        private String result;
        private String judgeReport;
        private LocalDateTime createdAt;
        private LocalDateTime finishedAt;
        private List<MessageResponse> messages;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessageResponse {
        private Long id;
        private String role;
        private Integer round;
        private String content;
        private Integer sequence;
        private LocalDateTime createdAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DebateListItem {
        private Long id;
        private String topic;
        private String status;
        private Integer currentRound;
        private Integer maxRounds;
        private String result;
        private LocalDateTime createdAt;
        private LocalDateTime finishedAt;
    }
}
