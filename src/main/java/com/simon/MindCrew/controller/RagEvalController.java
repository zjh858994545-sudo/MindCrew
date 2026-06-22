package com.simon.MindCrew.controller;

import com.simon.MindCrew.common.result.Result;
import com.simon.MindCrew.service.RagEvalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/rag-eval")
@RequiredArgsConstructor
public class RagEvalController {

    private final RagEvalService ragEvalService;

    @GetMapping("/strategies")
    public Result<List<String>> strategies() {
        return Result.success(ragEvalService.listStrategies());
    }

    @GetMapping("/cases")
    public Result<List<RagEvalService.EvalCase>> cases(
            @RequestParam(defaultValue = "true") boolean includeSecurity) {
        return Result.success(ragEvalService.listCases(includeSecurity));
    }

    @PostMapping("/runs")
    public Result<RagEvalService.EvaluationReport> run(@RequestBody(required = false) RagEvalService.EvaluationRequest request) {
        RagEvalService.EvaluationRequest safeRequest = request == null
                ? new RagEvalService.EvaluationRequest(null, 5, true)
                : request;
        return Result.success(ragEvalService.runEvaluation(safeRequest));
    }

    @GetMapping("/runs/latest")
    public Result<RagEvalService.EvaluationReport> latest() {
        return Result.success(ragEvalService.latestReport());
    }

    @GetMapping("/report")
    public Result<RagEvalService.EvaluationReport> report() {
        return Result.success(ragEvalService.latestReport());
    }
}
