package com.simon.MindCrew.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.simon.MindCrew.entity.RagEvalCase;
import com.simon.MindCrew.entity.RagEvalDataset;
import com.simon.MindCrew.mapper.RagEvalCaseMapper;
import com.simon.MindCrew.mapper.RagEvalDatasetMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void serviceDeskGoldenPairIsAddedAsDynamicEvalCase() throws Exception {
        RagEvalService service = new RagEvalService();
        RagEvalDatasetMapper datasetMapper = mock(RagEvalDatasetMapper.class);
        RagEvalCaseMapper caseMapper = mock(RagEvalCaseMapper.class);
        setField(service, "datasetMapper", datasetMapper);
        setField(service, "caseMapper", caseMapper);

        when(datasetMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(datasetMapper.insert(any(RagEvalDataset.class))).thenAnswer(invocation -> {
            RagEvalDataset dataset = invocation.getArgument(0);
            dataset.setId(7L);
            return 1;
        });
        when(caseMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(caseMapper.insert(any(RagEvalCase.class))).thenAnswer(invocation -> {
            RagEvalCase evalCase = invocation.getArgument(0);
            evalCase.setId(42L);
            return 1;
        });
        RagEvalCase persisted = new RagEvalCase();
        persisted.setId(42L);
        persisted.setDatasetId(7L);
        persisted.setQuestion("Can we export raw logs by email?");
        persisted.setExpectedAnswer("No. Use masking, approval, and controlled download.");
        persisted.setExpectedChunkIds("rag_case_42");
        persisted.setExpectedKeywords("security,export");
        persisted.setCategory("service_desk_security");
        persisted.setDifficulty("medium");
        persisted.setShouldRefuse(0);
        when(caseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(persisted));

        Long caseId = service.upsertServiceDeskGoldenPairCase(88L,
                persisted.getQuestion(), persisted.getExpectedAnswer(),
                "SECURITY", "Security-Data-Export-Policy", 7L);
        RagEvalService.EvaluationReport report = service.runEvaluation(
                new RagEvalService.EvaluationRequest(List.of("HYBRID_RERANK"), 5, true));

        assertEquals(42L, caseId);
        assertEquals(31, report.caseCount());
        assertEquals(13, report.corpusChunkCount());
        assertTrue(service.listCases(true).stream().anyMatch(c -> "db_case_42".equals(c.id())));
        verify(caseMapper).updateById(any(RagEvalCase.class));
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
