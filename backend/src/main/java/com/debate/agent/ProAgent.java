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
            String response = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
            log.info("[正方辩手] 生成发言成功，长度: {}", response.length());
            return response;
        } catch (Exception e) {
            log.error("[正方辩手] 生成发言失败", e);
            return "（正方辩手暂时无法发言，请稍后重试）";
        }
    }

    public Flux<String> streamGenerate(String topic, AgentConfig config, List<DebateMessage> history) {
        List<Message> messages = buildPrompt(topic, config, history);
        return chatModel.stream(new Prompt(messages))
                .filter(resp -> resp.getResult() != null && resp.getResult().getOutput() != null)
                .map(resp -> resp.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isEmpty());
    }

    private List<Message> buildPrompt(String topic, AgentConfig config, List<DebateMessage> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(config.getSystemPrompt()));

        StringBuilder context = new StringBuilder();
        context.append("本次辩论议题：").append(topic).append("\n\n");

        if (history.isEmpty()) {
            context.append("这是第一轮辩论，请你作为正方先发言，为议题立论。");
        } else {
            context.append("以下是之前的辩论记录，请针对反方的最新观点进行反驳，同时提出新的论据：\n\n");
            for (DebateMessage msg : history) {
                String roleName = "JUDGE".equals(msg.getRole()) ? "裁判" :
                        "PRO".equals(msg.getRole()) ? "正方" : "反方";
                context.append("【").append(roleName).append(" 第").append(msg.getRound()).append("轮】\n");
                context.append(msg.getContent()).append("\n\n");
            }
        }

        messages.add(new UserMessage(context.toString()));
        return messages;
    }
}
