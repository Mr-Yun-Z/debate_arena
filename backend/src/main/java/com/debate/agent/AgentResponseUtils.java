package com.debate.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Objects;

final class AgentResponseUtils {

    private static final String THINKING_SIGNATURE_KEY = "signature";

    private AgentResponseUtils() {
    }

    static List<AgentStreamChunk> toChunks(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return List.of();
        }

        return response.getResults().stream()
                .map(AgentResponseUtils::toChunk)
                .filter(Objects::nonNull)
                .toList();
    }

    static String answerText(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return "";
        }

        return response.getResults().stream()
                .filter(generation -> !isThinking(generation))
                .map(Generation::getOutput)
                .filter(Objects::nonNull)
                .map(AssistantMessage::getText)
                .filter(text -> text != null && !text.isEmpty())
                .reduce("", String::concat);
    }

    private static AgentStreamChunk toChunk(Generation generation) {
        if (generation == null || generation.getOutput() == null) {
            return null;
        }

        // Skip empty
        String text = generation.getOutput().getText();
        if (text == null || text.isEmpty()) {
            return null;
        }

        // Thinking
        AgentStreamChunk.Type type = isThinking(generation)
                ? AgentStreamChunk.Type.THINKING
                : AgentStreamChunk.Type.ANSWER;
        return new AgentStreamChunk(type, text);
    }

    /**
     * 判断是否为思考中
     * @param generation
     * @return
     */
    private static boolean isThinking(Generation generation) {
        // 获取输出
        AssistantMessage output = generation.getOutput();
        return output != null
                && output.getMetadata() != null
                && output.getMetadata().containsKey(THINKING_SIGNATURE_KEY);
    }
}
