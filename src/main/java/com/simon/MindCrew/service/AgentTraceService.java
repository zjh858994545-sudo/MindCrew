package com.simon.MindCrew.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.simon.MindCrew.entity.AgentTrace;
import com.simon.MindCrew.entity.AgentTraceSpan;
import com.simon.MindCrew.mapper.AgentTraceMapper;
import com.simon.MindCrew.mapper.AgentTraceSpanMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class AgentTraceService {

    private final Map<String, TraceRecord> traces = new ConcurrentHashMap<>();
    private final Map<String, List<SpanRecord>> spans = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private AgentTraceMapper traceMapper;

    @Autowired(required = false)
    private AgentTraceSpanMapper spanMapper;

    public TraceRecord startTrace(String userId, Long conversationId, String question, String modelName) {
        String traceId = "trace-" + UUID.randomUUID();
        TraceRecord trace = new TraceRecord(
                traceId,
                conversationId,
                userId,
                sanitize(question),
                null,
                "RUNNING",
                0L,
                modelName,
                LocalDateTime.now().toString()
        );
        traces.put(traceId, trace);
        spans.put(traceId, new CopyOnWriteArrayList<>());
        persistTrace(trace);
        log.info("[AgentTrace] start traceId={} conversationId={}", traceId, conversationId);
        return trace;
    }

    public void recordSpan(String traceId, String spanType, String name,
                           Object input, Object output, long latencyMs, String status, String errorMessage) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        SpanRecord span = new SpanRecord(
                "span-" + UUID.randomUUID(),
                traceId,
                spanType,
                name,
                sanitize(input),
                sanitize(output),
                latencyMs,
                status == null ? "OK" : status,
                sanitize(errorMessage),
                LocalDateTime.now().minusNanos(Math.max(0, latencyMs) * 1_000_000).toString(),
                LocalDateTime.now().toString()
        );
        spans.computeIfAbsent(traceId, k -> new CopyOnWriteArrayList<>()).add(span);
        persistSpan(span);
        log.debug("[AgentTrace] span traceId={} type={} name={} latencyMs={}", traceId, spanType, name, latencyMs);
    }

    public void finishTrace(String traceId, String answer, long totalLatencyMs) {
        TraceRecord old = traces.get(traceId);
        if (old == null) {
            return;
        }
        TraceRecord finished = new TraceRecord(
                old.traceId(), old.conversationId(), old.userId(), old.question(),
                sanitize(answer), "DONE", totalLatencyMs, old.modelName(), old.createdAt()
        );
        traces.put(traceId, finished);
        updateTrace(finished);
        log.info("[AgentTrace] finish traceId={} latencyMs={}", traceId, totalLatencyMs);
    }

    public void failTrace(String traceId, String errorMessage, long totalLatencyMs) {
        TraceRecord old = traces.get(traceId);
        if (old == null) {
            return;
        }
        TraceRecord failed = new TraceRecord(
                old.traceId(), old.conversationId(), old.userId(), old.question(),
                sanitize(errorMessage), "FAILED", totalLatencyMs, old.modelName(), old.createdAt()
        );
        traces.put(traceId, failed);
        updateTrace(failed);
        log.warn("[AgentTrace] fail traceId={} latencyMs={} error={}", traceId, totalLatencyMs, errorMessage);
    }

    public List<TraceRecord> listTraces() {
        if (traces.isEmpty() && traceMapper != null) {
            try {
                return traceMapper.selectList(new LambdaQueryWrapper<AgentTrace>()
                                .orderByDesc(AgentTrace::getCreateTime)
                                .last("LIMIT 50"))
                        .stream()
                        .map(this::toRecord)
                        .toList();
            } catch (Exception ex) {
                log.debug("[AgentTrace] skip db list fallback: {}", ex.getMessage());
            }
        }
        return traces.values().stream()
                .sorted(Comparator.comparing(TraceRecord::createdAt).reversed())
                .toList();
    }

    public TraceDetail getTrace(String traceId) {
        TraceRecord trace = traces.get(traceId);
        List<SpanRecord> spanList = spans.getOrDefault(traceId, List.of());
        if (trace == null && traceMapper != null) {
            try {
                AgentTrace entity = traceMapper.selectOne(new LambdaQueryWrapper<AgentTrace>()
                        .eq(AgentTrace::getTraceId, traceId)
                        .last("LIMIT 1"));
                trace = entity == null ? null : toRecord(entity);
            } catch (Exception ex) {
                log.debug("[AgentTrace] skip db trace fallback traceId={} error={}", traceId, ex.getMessage());
            }
        }
        if (spanList.isEmpty() && spanMapper != null) {
            try {
                spanList = spanMapper.selectList(new LambdaQueryWrapper<AgentTraceSpan>()
                                .eq(AgentTraceSpan::getTraceId, traceId)
                                .orderByAsc(AgentTraceSpan::getStartedAt))
                        .stream()
                        .map(this::toSpanRecord)
                        .toList();
            } catch (Exception ex) {
                log.debug("[AgentTrace] skip db spans fallback traceId={} error={}", traceId, ex.getMessage());
            }
        }
        return new TraceDetail(trace, spanList);
    }

    public Map<String, Object> summarizeRetrieval(String traceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<SpanRecord> spanList = spans.getOrDefault(traceId, List.of());
        out.put("traceId", traceId);
        out.put("vector", spanList.stream().filter(s -> "VECTOR_RETRIEVAL".equals(s.spanType())).count());
        out.put("bm25", spanList.stream().filter(s -> "BM25_RETRIEVAL".equals(s.spanType())).count());
        out.put("rrf", spanList.stream().filter(s -> "RRF_FUSION".equals(s.spanType())).count());
        out.put("rerank", spanList.stream().filter(s -> "RERANK".equals(s.spanType())).count());
        return out;
    }

    public String sanitize(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        text = text.replaceAll("(?i)(api[_-]?key|cookie|password|secret)\\s*[:=]\\s*[^,}\\s]+", "$1=***");
        text = text.replaceAll("(?i)(authorization)\\s*[:=]\\s*(?:Bearer\\s+)?[^,}\\s]+", "$1=***");
        text = text.replaceAll("(?i)bearer\\s+[a-z0-9._\\-]+", "Bearer ***");
        if (text.length() > 800) {
            text = text.substring(0, 800) + "...";
        }
        return text;
    }

    private void persistTrace(TraceRecord trace) {
        if (traceMapper == null) {
            return;
        }
        try {
            traceMapper.insert(toEntity(trace));
        } catch (Exception ex) {
            log.debug("[AgentTrace] skip trace persistence traceId={} error={}", trace.traceId(), ex.getMessage());
        }
    }

    private void updateTrace(TraceRecord trace) {
        if (traceMapper == null) {
            return;
        }
        try {
            AgentTrace entity = toEntity(trace);
            traceMapper.update(entity, new LambdaUpdateWrapper<AgentTrace>()
                    .eq(AgentTrace::getTraceId, trace.traceId()));
        } catch (Exception ex) {
            log.debug("[AgentTrace] skip trace update traceId={} error={}", trace.traceId(), ex.getMessage());
        }
    }

    private void persistSpan(SpanRecord span) {
        if (spanMapper == null) {
            return;
        }
        try {
            spanMapper.insert(toEntity(span));
        } catch (Exception ex) {
            log.debug("[AgentTrace] skip span persistence traceId={} spanType={} error={}",
                    span.traceId(), span.spanType(), ex.getMessage());
        }
    }

    private AgentTrace toEntity(TraceRecord trace) {
        AgentTrace entity = new AgentTrace();
        entity.setTraceId(trace.traceId());
        entity.setConversationId(trace.conversationId());
        entity.setUserId(trace.userId());
        entity.setQuestion(trace.question());
        entity.setAnswer(trace.answer());
        entity.setStatus(trace.status());
        entity.setTotalLatencyMs(trace.totalLatencyMs());
        entity.setModelName(trace.modelName());
        entity.setCreateTime(parseTime(trace.createdAt()));
        return entity;
    }

    private AgentTraceSpan toEntity(SpanRecord span) {
        AgentTraceSpan entity = new AgentTraceSpan();
        entity.setTraceId(span.traceId());
        entity.setSpanId(span.spanId());
        entity.setSpanType(span.spanType());
        entity.setName(span.name());
        entity.setInputSummary(span.inputSummary());
        entity.setOutputSummary(span.outputSummary());
        entity.setLatencyMs(span.latencyMs());
        entity.setStatus(span.status());
        entity.setErrorMessage(span.errorMessage());
        entity.setStartedAt(parseTime(span.startedAt()));
        entity.setEndedAt(parseTime(span.endedAt()));
        return entity;
    }

    private TraceRecord toRecord(AgentTrace entity) {
        return new TraceRecord(
                entity.getTraceId(),
                entity.getConversationId(),
                entity.getUserId(),
                entity.getQuestion(),
                entity.getAnswer(),
                entity.getStatus(),
                entity.getTotalLatencyMs(),
                entity.getModelName(),
                formatTime(entity.getCreateTime())
        );
    }

    private SpanRecord toSpanRecord(AgentTraceSpan entity) {
        return new SpanRecord(
                entity.getSpanId(),
                entity.getTraceId(),
                entity.getSpanType(),
                entity.getName(),
                entity.getInputSummary(),
                entity.getOutputSummary(),
                entity.getLatencyMs(),
                entity.getStatus(),
                entity.getErrorMessage(),
                formatTime(entity.getStartedAt()),
                formatTime(entity.getEndedAt())
        );
    }

    private LocalDateTime parseTime(String time) {
        return time == null ? null : LocalDateTime.parse(time);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : time.toString();
    }

    public record TraceRecord(String traceId, Long conversationId, String userId,
                              String question, String answer, String status,
                              Long totalLatencyMs, String modelName, String createdAt) {}
    public record SpanRecord(String spanId, String traceId, String spanType, String name,
                             String inputSummary, String outputSummary,
                             Long latencyMs, String status, String errorMessage,
                             String startedAt, String endedAt) {}
    public record TraceDetail(TraceRecord trace, List<SpanRecord> spans) {}
}
