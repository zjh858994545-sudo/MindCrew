package com.simon.MindCrew.service.rag;

import com.simon.MindCrew.agent.QueryRouter;
import com.simon.MindCrew.config.AiConfigHolder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryPlannerServiceTest {

    @Test
    void createsPlanWithVariantsHydeAndRetryPolicy() {
        QueryPlannerService service = serviceReturning("compound");

        QueryPlannerService.QueryPlan plan = service.plan(
                "A 方案和 B 方案有什么区别", "A 方案 B 方案 对比", "u1", List.of(1L));

        assertEquals(QueryRouter.COMPOUND, plan.intentType());
        assertTrue(plan.queryVariants().size() >= 2);
        assertFalse(plan.hydeDocument().isBlank());
        assertTrue(plan.retryPolicy().enabled());
        assertTrue(plan.retryPolicy().enableWebSearchOnRetry());
    }

    @Test
    void marksLowConfidenceRetrievalWhenScoreAndCountAreWeak() {
        QueryPlannerService service = serviceReturning("knowledge_query");
        QueryPlannerService.QueryPlan plan = service.plan("怎么配置权限", "配置权限", "u1", List.of());

        RetrievedChunk chunk = new RetrievedChunk();
        chunk.setContent("弱相关内容");
        chunk.setScore(0.03f);
        chunk.setSource(RetrievedChunk.Source.VECTOR);

        QueryPlannerService.RetrievalQuality quality = service.assessRetrieval(plan, List.of(chunk));

        assertTrue(quality.lowConfidence());
        assertTrue(quality.reasons().contains("chunk_count_below_3"));
    }

    private QueryPlannerService serviceReturning(String response) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(anyString())).thenReturn(response);

        AiConfigHolder aiConfigHolder = mock(AiConfigHolder.class);
        when(aiConfigHolder.getChatModel()).thenReturn(chatModel);

        QueryRouter router = new QueryRouter(aiConfigHolder);
        return new QueryPlannerService(router, aiConfigHolder);
    }
}
