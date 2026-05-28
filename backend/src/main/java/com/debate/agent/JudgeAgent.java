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
public class JudgeAgent {

    private final ChatModel chatModel;

    public String generate(String topic, AgentConfig config, List<DebateMessage> history) {
        List<Message> messages = buildPrompt(topic, config, history);
        try {
            ChatResponse chatResponse = chatModel.call(new Prompt(messages));
            String response = AgentResponseUtils.answerText(chatResponse);
            log.info("[裁判] 生成评审报告成功，长度: {}", response.length());
            return response;
        } catch (Exception e) {
            log.error("[裁判] 生成评审报告失败", e);
            return "（裁判暂时无法出具评审报告，请稍后重试）";
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
        context.append("以下是本场辩论的完整记录，请进行评审：\n\n");

        for (DebateMessage msg : history) {
            String roleName = "PRO".equals(msg.getRole()) ? "正方" : "反方";
            context.append("【").append(roleName).append(" 第").append(msg.getRound()).append("轮】\n");
            context.append(msg.getContent()).append("\n\n");
        }

        messages.add(new UserMessage(context.toString()));
        return messages;
    }
}
