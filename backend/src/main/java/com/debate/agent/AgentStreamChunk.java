package com.debate.agent;

public record AgentStreamChunk(Type type, String content) {

    public enum Type {
        THINKING,
        ANSWER
    }

    public boolean isThinking() {
        return type == Type.THINKING;
    }

    public boolean isAnswer() {
        return type == Type.ANSWER;
    }
}
