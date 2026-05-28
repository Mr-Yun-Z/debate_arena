package com.debate.service;

import com.debate.agent.ConAgent;
import com.debate.agent.JudgeAgent;
import com.debate.agent.ProAgent;
import com.debate.dto.DebateDTOs.*;
import com.debate.entity.AgentConfig;
import com.debate.entity.Debate;
import com.debate.entity.Debate.DebateStatus;
import com.debate.entity.DebateMessage;
import com.debate.repository.AgentConfigRepository;
import com.debate.repository.DebateMessageRepository;
import com.debate.repository.DebateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebateService {

    private final DebateRepository debateRepository;
    private final DebateMessageRepository messageRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final ProAgent proAgent;
    private final ConAgent conAgent;
    private final JudgeAgent judgeAgent;

    @Value("${app.debate.max-rounds:5}")
    private int defaultMaxRounds;

    // ========== 同步接口（保持兼容） ==========

    @Transactional
    public DebateResponse createDebate(CreateDebateRequest request) {
        int maxRounds = (request.getMaxRounds() != null && request.getMaxRounds() > 0)
                ? request.getMaxRounds() : defaultMaxRounds;

        Debate debate = Debate.builder()
                .topic(request.getTopic())
                .status(DebateStatus.PENDING)
                .currentRound(0)
                .maxRounds(maxRounds)
                .build();
        debate = debateRepository.save(debate);

        log.info("创建辩论成功, id={}, topic={}", debate.getId(), debate.getTopic());
        return toResponse(debate);
    }

    @Transactional
    public DebateResponse startDebate(Long debateId) {
        Debate debate = getDebate(debateId);
        if (debate.getStatus() != DebateStatus.PENDING) {
            throw new RuntimeException("辩论已开始或已结束");
        }

        debate.setStatus(DebateStatus.RUNNING);
        debate.setCurrentRound(1);
        debate.setCurrentSpeaker("PRO");

        AgentConfig proConfig = agentConfigRepository.findByRole("PRO").orElseThrow();
        List<DebateMessage> history = messageRepository.findByDebateIdOrderBySequenceAsc(debateId);
        String proContent = proAgent.generate(debate.getTopic(), proConfig, history);

        saveMessage(debate, "PRO", 1, proContent);
        debate.setCurrentSpeaker("CON");
        debate = debateRepository.save(debate);

        log.info("辩论开始, id={}, 正方已发言", debateId);
        return toResponse(debate);
    }

    @Transactional
    public DebateResponse advanceDebate(Long debateId) {
        Debate debate = getDebate(debateId);
        if (debate.getStatus() != DebateStatus.RUNNING) {
            throw new RuntimeException("辩论未在进行中");
        }

        List<DebateMessage> history = messageRepository.findByDebateIdOrderBySequenceAsc(debateId);

        if ("CON".equals(debate.getCurrentSpeaker())) {
            AgentConfig conConfig = agentConfigRepository.findByRole("CON").orElseThrow();
            String conContent = conAgent.generate(debate.getTopic(), conConfig, history);
            saveMessage(debate, "CON", debate.getCurrentRound(), conContent);

            if (debate.getCurrentRound() >= debate.getMaxRounds()) {
                debate.setStatus(DebateStatus.FINISHED);
                debate.setCurrentSpeaker("JUDGE");
                debate.setFinishedAt(LocalDateTime.now());
                log.info("辩论达到最大轮次, id={}, 进入裁判评审", debateId);
            } else {
                int nextRound = debate.getCurrentRound() + 1;
                debate.setCurrentRound(nextRound);
                debate.setCurrentSpeaker("PRO");

                history = messageRepository.findByDebateIdOrderBySequenceAsc(debateId);
                AgentConfig proConfig = agentConfigRepository.findByRole("PRO").orElseThrow();
                String proContent = proAgent.generate(debate.getTopic(), proConfig, history);
                saveMessage(debate, "PRO", nextRound, proContent);

                debate.setCurrentSpeaker("CON");
            }
        } else {
            throw new RuntimeException("当前轮次未完成，状态异常: " + debate.getCurrentSpeaker());
        }

        debate = debateRepository.save(debate);
        return toResponse(debate);
    }

    @Transactional
    public DebateResponse terminateDebate(Long debateId) {
        Debate debate = getDebate(debateId);
        if (debate.getStatus() == DebateStatus.FINISHED || debate.getStatus() == DebateStatus.TERMINATED) {
            throw new RuntimeException("辩论已结束");
        }
        debate.setStatus(DebateStatus.TERMINATED);
        debate.setCurrentSpeaker("JUDGE");
        debate.setFinishedAt(LocalDateTime.now());
        debate = debateRepository.save(debate);

        log.info("辩论手动终止, id={}", debateId);
        return toResponse(debate);
    }

    @Transactional
    public DebateResponse judgeDebate(Long debateId) {
        Debate debate = getDebate(debateId);
        if (debate.getStatus() != DebateStatus.FINISHED && debate.getStatus() != DebateStatus.TERMINATED) {
            throw new RuntimeException("辩论尚未结束");
        }
        if (debate.getJudgeReport() != null) {
            return toResponse(debate);
        }

        AgentConfig judgeConfig = agentConfigRepository.findByRole("JUDGE").orElseThrow();
        List<DebateMessage> history = messageRepository.findByDebateIdOrderBySequenceAsc(debateId);
        String report = judgeAgent.generate(debate.getTopic(), judgeConfig, history);

        debate.setJudgeReport(report);
        debate.setCurrentSpeaker(null);
        debate = debateRepository.save(debate);

        log.info("裁判评审完成, id={}", debateId);
        return toResponse(debate);
    }

    @Transactional(readOnly = true)
    public DebateResponse getDebateDetail(Long debateId) {
        return toResponse(getDebate(debateId));
    }

    @Transactional(readOnly = true)
    public List<DebateListItem> getAllDebates() {
        return debateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional
    public void deleteDebate(Long debateId) {
        debateRepository.deleteById(debateId);
    }

    // ========== 流式接口 ==========

    /** 流式开始辩论：正方发言 → 保存 → 返回完整 DebateResponse */
    @Transactional
    public Flux<ServerSentEvent<String>> streamStartDebate(Long debateId) {
        Debate debate = getDebate(debateId);
        if (debate.getStatus() != DebateStatus.PENDING) {
            return Flux.error(new RuntimeException("辩论已开始或已结束"));
        }

        debate.setStatus(DebateStatus.RUNNING);
        debate.setCurrentRound(1);
        debate.setCurrentSpeaker("PRO");
        debateRepository.save(debate);

        AgentConfig proConfig = agentConfigRepository.findByRole("PRO").orElseThrow();
        List<DebateMessage> history = messageRepository.findByDebateIdOrderBySequenceAsc(debateId);

        AtomicReference<StringBuilder> contentRef = new AtomicReference<>(new StringBuilder());

        return proAgent.streamGenerate(debate.getTopic(), proConfig, history)
                .map(chunk -> {
                    contentRef.get().append(chunk);
                    return ServerSentEvent.<String>builder()
                            .event("token")
                            .data("{\"role\":\"PRO\",\"content\":" + toJsonString(chunk) + "}")
                            .build();
                })
                .concatWith(Mono.defer(() -> {
                    String fullContent = contentRef.get().toString();
                    saveMessage(debate, "PRO", 1, fullContent);
                    debate.setCurrentSpeaker("CON");
                    debateRepository.save(debate);

                    log.info("辩论开始(流式), id={}, 正方已发言, 长度={}", debateId, fullContent.length());
                    Debate saved = getDebate(debateId);
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("done")
                            .data(toJson(toResponse(saved)))
                            .build());
                }))
                .onErrorResume(e -> {
                    log.error("流式开始辩论失败", e);
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("生成失败: " + e.getMessage())
                            .build());
                });
    }

    /** 流式推进辩论：反方发言 → (下一轮正方发言) → 保存 → 返回完整 DebateResponse */
    @Transactional
    public Flux<ServerSentEvent<String>> streamAdvanceDebate(Long debateId) {
        Debate debate = getDebate(debateId);
        if (debate.getStatus() != DebateStatus.RUNNING) {
            return Flux.error(new RuntimeException("辩论未在进行中"));
        }
        if (!"CON".equals(debate.getCurrentSpeaker())) {
            return Flux.error(new RuntimeException("当前轮次未完成，状态异常: " + debate.getCurrentSpeaker()));
        }

        List<DebateMessage> history = messageRepository.findByDebateIdOrderBySequenceAsc(debateId);
        AgentConfig conConfig = agentConfigRepository.findByRole("CON").orElseThrow();
        AtomicReference<StringBuilder> conContentRef = new AtomicReference<>(new StringBuilder());

        // 反方发言流
        Flux<ServerSentEvent<String>> conStream = conAgent.streamGenerate(
                debate.getTopic(), conConfig, history)
                .map(chunk -> {
                    conContentRef.get().append(chunk);
                    return ServerSentEvent.<String>builder()
                            .event("token")
                            .data("{\"role\":\"CON\",\"content\":" + toJsonString(chunk) + "}")
                            .build();
                });

        // 反方结束后决定是否继续
        return conStream.concatWith(Flux.defer(() -> {
            Debate d = getDebate(debateId);
            String conContent = conContentRef.get().toString();
            saveMessage(d, "CON", d.getCurrentRound(), conContent);
            log.info("反方已发言(流式), 长度={}", conContent.length());

            if (d.getCurrentRound() >= d.getMaxRounds()) {
                d.setStatus(DebateStatus.FINISHED);
                d.setCurrentSpeaker("JUDGE");
                d.setFinishedAt(LocalDateTime.now());
                debateRepository.save(d);
                log.info("辩论达到最大轮次(流式), id={}", debateId);
                return Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data(toJson(toResponse(d)))
                        .build());
            }

            int nextRound = d.getCurrentRound() + 1;
            d.setCurrentRound(nextRound);
            d.setCurrentSpeaker("PRO");
            debateRepository.save(d);

            AgentConfig proConfig = agentConfigRepository.findByRole("PRO").orElseThrow();
            List<DebateMessage> proHistory = messageRepository.findByDebateIdOrderBySequenceAsc(debateId);
            AtomicReference<StringBuilder> proContentRef = new AtomicReference<>(new StringBuilder());

            Flux<ServerSentEvent<String>> proStream = proAgent.streamGenerate(
                    d.getTopic(), proConfig, proHistory)
                    .map(chunk -> {
                        proContentRef.get().append(chunk);
                        return ServerSentEvent.<String>builder()
                                .event("token")
                                .data("{\"role\":\"PRO\",\"content\":" + toJsonString(chunk) + "}")
                                .build();
                    });

            ServerSentEvent<String> speakerEvent = ServerSentEvent.<String>builder()
                    .event("speaker")
                    .data("{\"role\":\"PRO\",\"round\":" + nextRound + "}")
                    .build();

            Flux<ServerSentEvent<String>> proWithDone = Flux.concat(
                    proStream,
                    Mono.defer(() -> {
                        String proContent = proContentRef.get().toString();
                        Debate dd = getDebate(debateId);
                        saveMessage(dd, "PRO", nextRound, proContent);
                        dd.setCurrentSpeaker("CON");
                        debateRepository.save(dd);
                        log.info("正方已发言(流式), 第{}轮, 长度={}", nextRound, proContent.length());
                        return Mono.just(ServerSentEvent.<String>builder()
                                .event("done")
                                .data(toJson(toResponse(dd)))
                                .build());
                    }));

            return Flux.concat(Mono.just(speakerEvent), proWithDone);
        })).onErrorResume(e -> {
            log.error("流式推进辩论失败", e);
            return Mono.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("生成失败: " + e.getMessage())
                    .build());
        });
    }

    /** 流式裁判评审 */
    @Transactional
    public Flux<ServerSentEvent<String>> streamJudgeDebate(Long debateId) {
        Debate debate = getDebate(debateId);
        if (debate.getStatus() != DebateStatus.FINISHED && debate.getStatus() != DebateStatus.TERMINATED) {
            return Flux.error(new RuntimeException("辩论尚未结束"));
        }
        if (debate.getJudgeReport() != null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("done")
                    .data(toJson(toResponse(debate)))
                    .build());
        }

        AgentConfig judgeConfig = agentConfigRepository.findByRole("JUDGE").orElseThrow();
        List<DebateMessage> history = messageRepository.findByDebateIdOrderBySequenceAsc(debateId);

        AtomicReference<StringBuilder> contentRef = new AtomicReference<>(new StringBuilder());

        return judgeAgent.streamGenerate(debate.getTopic(), judgeConfig, history)
                .map(chunk -> {
                    contentRef.get().append(chunk);
                    return ServerSentEvent.<String>builder()
                            .event("token")
                            .data("{\"role\":\"JUDGE\",\"content\":" + toJsonString(chunk) + "}")
                            .build();
                })
                .concatWith(Mono.defer(() -> {
                    String fullContent = contentRef.get().toString();
                    Debate d = getDebate(debateId);
                    d.setJudgeReport(fullContent);
                    d.setCurrentSpeaker(null);
                    debateRepository.save(d);

                    log.info("裁判评审完成(流式), id={}, 长度={}", debateId, fullContent.length());
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("done")
                            .data(toJson(toResponse(d)))
                            .build());
                }))
                .onErrorResume(e -> {
                    log.error("流式裁判评审失败", e);
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("生成失败: " + e.getMessage())
                            .build());
                });
    }

    // ========== 私有方法 ==========

    private Debate getDebate(Long id) {
        return debateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("辩论不存在, id=" + id));
    }

    private void saveMessage(Debate debate, String role, int round, String content) {
        int count = messageRepository.findByDebateIdOrderBySequenceAsc(debate.getId()).size();
        messageRepository.save(DebateMessage.builder()
                .debate(debate)
                .role(role)
                .round(round)
                .content(content)
                .sequence(count + 1)
                .build());
    }

    private DebateResponse toResponse(Debate debate) {
        List<MessageResponse> messages = messageRepository.findByDebateIdOrderBySequenceAsc(debate.getId())
                .stream().map(this::toMessageResponse).toList();

        return DebateResponse.builder()
                .id(debate.getId())
                .topic(debate.getTopic())
                .status(debate.getStatus().name())
                .currentRound(debate.getCurrentRound())
                .currentSpeaker(debate.getCurrentSpeaker())
                .maxRounds(debate.getMaxRounds())
                .proScore(debate.getProScore())
                .conScore(debate.getConScore())
                .result(debate.getResult())
                .judgeReport(debate.getJudgeReport())
                .createdAt(debate.getCreatedAt())
                .finishedAt(debate.getFinishedAt())
                .messages(messages)
                .build();
    }

    private MessageResponse toMessageResponse(DebateMessage msg) {
        return MessageResponse.builder()
                .id(msg.getId())
                .role(msg.getRole())
                .round(msg.getRound())
                .content(msg.getContent())
                .sequence(msg.getSequence())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private DebateListItem toListItem(Debate debate) {
        return DebateListItem.builder()
                .id(debate.getId())
                .topic(debate.getTopic())
                .status(debate.getStatus().name())
                .currentRound(debate.getCurrentRound())
                .maxRounds(debate.getMaxRounds())
                .result(debate.getResult())
                .createdAt(debate.getCreatedAt())
                .finishedAt(debate.getFinishedAt())
                .build();
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String toJsonString(String text) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(text);
        } catch (Exception e) {
            return "\"\"";
        }
    }
}
