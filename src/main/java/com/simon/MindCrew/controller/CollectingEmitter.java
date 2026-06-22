package com.simon.MindCrew.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory SSE collector used by non-streaming internal callers.
 */
@Getter
public class CollectingEmitter extends SseEmitter {

    private final StringBuilder answerBuf = new StringBuilder();
    private final List<Map<String, Object>> sources = new ArrayList<>();
    private final Map<String, Object> donePayload = new LinkedHashMap<>();
    private final Map<String, Object> queryPlan = new LinkedHashMap<>();
    private final Map<String, Object> retrievalLog = new LinkedHashMap<>();
    private final List<Map<String, Object>> agentTrace = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private int inputTokens = 0;
    private int outputTokens = 0;
    private Long conversationId;
    private String traceId;
    private String intentType;
    private boolean fallback;
    private boolean emergency;
    private Boolean reflectionPassed;

    public CollectingEmitter() {
        super(300_000L);
    }

    @Override
    public void send(SseEventBuilder builder) {
        try {
            Set<DataWithMediaType> events = builder.build();
            for (DataWithMediaType event : events) {
                Object data = event.getData();
                if (data == null) continue;
                String raw = data.toString();
                if (!raw.startsWith("event:")) {
                    tryHandleAsJsonEvent(raw);
                }
            }
        } catch (Exception ignored) {
            // A collector must never break the main Agent flow.
        }
    }

    @Override
    public void send(Object obj) {
        if (obj instanceof SseEventBuilder builder) {
            send(builder);
        } else if (obj != null) {
            tryHandleAsJsonEvent(obj.toString());
        }
    }

    @Override
    public void send(Object obj, MediaType mediaType) {
        send(obj);
    }

    @Override
    public void complete() {
        // no HTTP response
    }

    @Override
    public void completeWithError(Throwable ex) {
        if (ex != null) {
            errors.add(ex.getMessage());
        }
    }

    private void tryHandleAsJsonEvent(String raw) {
        if (raw == null || raw.isBlank()) return;
        String text = raw.trim();
        if (!text.startsWith("{") && !text.startsWith("[")) return;
        try {
            JSONObject data = JSON.parseObject(text);
            if (data.containsKey("content")) {
                collectToken(data);
                return;
            }
            if (data.containsKey("error") || looksLikeErrorMessage(data)) {
                Object msg = data.getOrDefault("error", data.get("message"));
                if (msg != null) errors.add(String.valueOf(msg));
            }
            if (data.containsKey("intentType") && data.containsKey("queryVariants")) {
                queryPlan.clear();
                queryPlan.putAll(data);
                Object intent = data.get("intentType");
                if (intent != null) intentType = String.valueOf(intent);
                return;
            }
            if (data.containsKey("sources") || data.containsKey("retrievalLog") || data.containsKey("traceId")) {
                donePayload.clear();
                donePayload.putAll(data);
                collectSources(data.get("sources"));
                collectRetrievalLog(data.get("retrievalLog"));
                collectAgentTrace(data.get("agentTrace"));
                collectDoneFields(data);
                return;
            }
            if (data.containsKey("original")) {
                inputTokens += Math.max(1, String.valueOf(data.get("original")).length() / 2);
            }
        } catch (Exception ignored) {
            // Ignore non-Agent JSON payloads.
        }
    }

    private void collectToken(JSONObject data) {
        Object content = data.get("content");
        if (content == null) return;
        String token = content.toString();
        answerBuf.append(token);
        outputTokens += Math.max(1, token.length() / 2);
    }

    private void collectSources(Object rawSources) {
        if (!(rawSources instanceof JSONArray array)) return;
        sources.clear();
        for (Object item : array) {
            if (item instanceof Map) {
                //noinspection unchecked
                sources.add(new LinkedHashMap<>((Map<String, Object>) item));
            } else if (item instanceof JSONObject object) {
                sources.add(new LinkedHashMap<>(object));
            }
        }
    }

    private void collectRetrievalLog(Object rawLog) {
        if (rawLog instanceof JSONObject object) {
            retrievalLog.clear();
            retrievalLog.putAll(object);
        } else if (rawLog instanceof Map<?, ?> map) {
            retrievalLog.clear();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                retrievalLog.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
    }

    private void collectAgentTrace(Object rawTrace) {
        if (!(rawTrace instanceof JSONArray array)) return;
        agentTrace.clear();
        for (Object item : array) {
            if (item instanceof JSONObject object) {
                agentTrace.add(new LinkedHashMap<>(object));
            } else if (item instanceof Map) {
                //noinspection unchecked
                agentTrace.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
    }

    private void collectDoneFields(JSONObject data) {
        Object cid = data.get("conversationId");
        if (cid instanceof Number number) {
            conversationId = number.longValue();
        } else if (cid != null) {
            try {
                conversationId = Long.parseLong(String.valueOf(cid));
            } catch (NumberFormatException ignored) {
                // leave null
            }
        }
        Object traceValue = data.get("traceId");
        if (traceValue != null) traceId = String.valueOf(traceValue);
        Object intent = data.get("intentType");
        if (intent != null) intentType = String.valueOf(intent);
        Object fallbackValue = data.get("isFallback");
        if (fallbackValue instanceof Boolean value) fallback = value;
        Object emergencyValue = data.get("isEmergency");
        if (emergencyValue instanceof Boolean value) emergency = value;
        Object reflectionValue = data.get("reflectionPassed");
        if (reflectionValue instanceof Boolean value) reflectionPassed = value;
    }

    private boolean looksLikeErrorMessage(JSONObject data) {
        Object message = data.get("message");
        if (message == null) return false;
        String text = String.valueOf(message);
        return text.contains("失败") || text.contains("异常") || text.toLowerCase().contains("error");
    }

    public String getAnswer() {
        return answerBuf.toString();
    }
}
