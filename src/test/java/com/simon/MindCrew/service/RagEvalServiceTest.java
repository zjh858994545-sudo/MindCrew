package com.simon.MindCrew.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagEvalServiceTest {

    private final RagEvalService service = new RagEvalService();

    @Test
    void runsBuiltInGoldenQaAndExportsReport() {
        RagEvalService.EvaluationReport report = service.runEvaluation(
                new RagEvalService.EvaluationRequest(null, 5, true));

        assertEquals(30, report.caseCount());
        assertEquals(4, report.strategies().size());
        assertFalse(report.strategies().get(0).results().isEmpty());
        assertNotNull(report.reportPath());
        assertTrue(Files.exists(Path.of(report.reportPath())));
    }
}
