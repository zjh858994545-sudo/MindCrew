package com.simon.MindCrew.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentTraceServiceTest {

    private final AgentTraceService service = new AgentTraceService();

    @Test
    void recordsSanitizedSpansAndRetrievalSummary() {
        AgentTraceService.TraceRecord trace = service.startTrace(
                "user-1", 7L, "question api_key=raw-secret", "qwen-plus");

        service.recordSpan(trace.traceId(), "VECTOR_RETRIEVAL", "vector_results",
                "authorization: Bearer abc.def", "count=3", 12, "OK", null);
        service.recordSpan(trace.traceId(), "BM25_RETRIEVAL", "bm25_results",
                "cookie=session-secret", "count=2", 8, "OK", null);
        service.finishTrace(trace.traceId(), "final answer password=hidden", 30);

        AgentTraceService.TraceDetail detail = service.getTrace(trace.traceId());

        assertNotNull(detail.trace());
        assertEquals("DONE", detail.trace().status());
        assertEquals(2, detail.spans().size());
        assertFalse(detail.trace().question().contains("raw-secret"));
        assertFalse(detail.trace().answer().contains("hidden"));
        assertFalse(detail.spans().get(0).inputSummary().contains("abc.def"));
        assertEquals(1L, service.summarizeRetrieval(trace.traceId()).get("vector"));
        assertEquals(1L, service.summarizeRetrieval(trace.traceId()).get("bm25"));
    }
}
