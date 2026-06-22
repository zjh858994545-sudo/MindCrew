package com.simon.MindCrew.controller;

import com.simon.MindCrew.common.result.Result;
import com.simon.MindCrew.service.AgentTraceService;
import com.simon.MindCrew.service.SafetyGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/agent-traces")
@RequiredArgsConstructor
public class AgentTraceController {

    private final AgentTraceService traceService;
    private final SafetyGuardService safetyGuardService;

    @GetMapping
    public Result<List<AgentTraceService.TraceRecord>> list() {
        return Result.success(traceService.listTraces());
    }

    @GetMapping("/safety-events")
    public Result<List<SafetyGuardService.SafetyEvent>> safetyEvents() {
        return Result.success(safetyGuardService.listEvents());
    }

    @GetMapping("/{traceId}")
    public Result<AgentTraceService.TraceDetail> detail(@PathVariable String traceId) {
        return Result.success(traceService.getTrace(traceId));
    }

    @GetMapping("/{traceId}/retrieval")
    public Result<Map<String, Object>> retrieval(@PathVariable String traceId) {
        return Result.success(traceService.summarizeRetrieval(traceId));
    }
}
