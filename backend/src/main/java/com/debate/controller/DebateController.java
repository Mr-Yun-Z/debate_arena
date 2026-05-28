package com.debate.controller;

import com.debate.dto.ApiResponse;
import com.debate.dto.DebateDTOs.*;
import com.debate.service.DebateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/debate")
@RequiredArgsConstructor
public class DebateController {

    private final DebateService debateService;

    /** 创建辩论 */
    @PostMapping
    public ApiResponse<DebateResponse> create(@RequestBody @Valid CreateDebateRequest request) {
        return ApiResponse.success(debateService.createDebate(request));
    }

    /** 获取所有辩论列表 */
    @GetMapping
    public ApiResponse<List<DebateListItem>> list() {
        return ApiResponse.success(debateService.getAllDebates());
    }

    /** 获取辩论详情 */
    @GetMapping("/{id}")
    public ApiResponse<DebateResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(debateService.getDebateDetail(id));
    }

    /** 开始辩论（同步） */
    @PostMapping("/{id}/start")
    public ApiResponse<DebateResponse> start(@PathVariable Long id) {
        return ApiResponse.success(debateService.startDebate(id));
    }

    /** 推进辩论（同步） */
    @PostMapping("/{id}/advance")
    public ApiResponse<DebateResponse> advance(@PathVariable Long id) {
        return ApiResponse.success(debateService.advanceDebate(id));
    }

    /** 终止辩论 */
    @PostMapping("/{id}/terminate")
    public ApiResponse<DebateResponse> terminate(@PathVariable Long id) {
        return ApiResponse.success(debateService.terminateDebate(id));
    }

    /** 裁判评审（同步） */
    @PostMapping("/{id}/judge")
    public ApiResponse<DebateResponse> judge(@PathVariable Long id) {
        return ApiResponse.success(debateService.judgeDebate(id));
    }

    /** 删除辩论 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        debateService.deleteDebate(id);
        return ApiResponse.success("删除成功", null);
    }

    // ========== 流式 SSE 接口 ==========

    /** 流式开始辩论 */
    @PostMapping(value = "/{id}/stream/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamStart(@PathVariable Long id) {
        return debateService.streamStartDebate(id);
    }

    /** 流式推进辩论 */
    @PostMapping(value = "/{id}/stream/advance", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamAdvance(@PathVariable Long id) {
        return debateService.streamAdvanceDebate(id);
    }

    /** 流式裁判评审 */
    @PostMapping(value = "/{id}/stream/judge", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamJudge(@PathVariable Long id) {
        return debateService.streamJudgeDebate(id);
    }
}
