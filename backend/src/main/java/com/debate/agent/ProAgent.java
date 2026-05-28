package com.debate.agent;

import com.debate.entity.AgentConfig;
import com.debate.entity.DebateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProAgent {

    private final ChatModel chatModel;

    public String generate(String topic, AgentConfig config, List<DebateMessage> history) {
        List<Message> messages = buildPrompt(topic, config, history);
        try {
            ChatResponse chatResponse = chatModel.call(new Prompt(messages));
            String response = AgentResponseUtils.answerText(chatResponse);
            log.info("[正方辩手] 生成发言成功，长度: {}", response.length());
            return response;
        } catch (Exception e) {
            log.error("[正方辩手] 生成发言失败", e);
            return "（正方辩手暂时无法发言，请稍后重试）";
        }
    }

    public Flux<AgentStreamChunk> streamGenerate(String topic, AgentConfig config, List<DebateMessage> history) {
        List<Message> messages = buildPrompt(topic, config, history);
        return chatModel.stream(new Prompt(messages))
                .flatMapIterable(AgentResponseUtils::toChunks);
    }

    private List<Message> buildPrompt(String topic, AgentConfig config, List<DebateMessage> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(config.getSystemPrompt()));

        StringBuilder context = new StringBuilder();
        context.append("本次辩论议题：").append(topic).append("\n\n");

        if (history.isEmpty()) {
            context.append("这是第一轮辩论，请你作为支持队先开麦。用大白话说清楚，别端着，别写论文。");
        } else {
            context.append("以下是之前的辩论记录，请接住反对队最新观点，用接地气的话反击，再补一个新的现实例子：\n\n");
            for (DebateMessage msg : history) {
                String roleName = "JUDGE".equals(msg.getRole()) ? "裁判" :
                        "PRO".equals(msg.getRole()) ? "正方" : "反方";
                context.append("【").append(roleName).append(" 第").append(msg.getRound()).append("轮】\n");
                context.append(msg.getContent()).append("\n\n");
            }
        }

        context.append("要求：120-220字，短句，像朋友聊天，最多讲3个点，结尾来一句好记的话。");
        messages.add(new UserMessage(context.toString()));
        return messages;
    }
}
