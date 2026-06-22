package com.simon.MindCrew.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.MindCrew.agent.AgentToolContext;
import com.simon.MindCrew.entity.McpAuditLog;
import com.simon.MindCrew.entity.McpClient;
import com.simon.MindCrew.entity.McpToolPolicy;
import com.simon.MindCrew.entity.McpToolRegistry;
import com.simon.MindCrew.mapper.McpAuditLogMapper;
import com.simon.MindCrew.mapper.McpClientMapper;
import com.simon.MindCrew.mapper.McpToolPolicyMapper;
import com.simon.MindCrew.mapper.McpToolRegistryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.lang.reflect.Array;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpGovernanceService {

    public static final String INTERNAL_AGENT_CLIENT = "internal-agent";
    public static final String PUBLIC_MCP_CLIENT = "public-mcp";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final McpClientMapper clientMapper;
    private final McpToolPolicyMapper policyMapper;
    private final McpAuditLogMapper auditLogMapper;
    private final McpToolRegistryMapper toolRegistryMapper;

    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public Decision checkAndStart(String clientId, String userId, String toolName, Object input) {
        String effectiveClientId = normalizeClientId(clientId);
        String effectiveUserId = userId != null ? userId : currentUserId();
        String requestId = "mcp-" + UUID.randomUUID();
        long startTime = System.currentTimeMillis();

        try {
            McpToolRegistry tool = findTool(toolName);
            if (tool != null && "disabled".equals(tool.getStatus())) {
                Decision decision = Decision.denied(requestId, effectiveClientId, effectiveUserId, toolName,
                        "tool_disabled", startTime, summarize(input));
                record(decision, "BLOCK", 0, null, decision.reason());
                return decision;
            }

            McpClient client = findClient(effectiveClientId);
            if (client != null && !"active".equals(client.getStatus())) {
                Decision decision = Decision.denied(requestId, effectiveClientId, effectiveUserId, toolName,
                        "client_disabled", startTime, summarize(input));
                record(decision, "BLOCK", 0, null, decision.reason());
                return decision;
            }

            McpToolPolicy policy = findPolicy(effectiveClientId, toolName);
            if (policy != null && policy.getEnabled() != null && policy.getEnabled() == 0) {
                Decision decision = Decision.denied(requestId, effectiveClientId, effectiveUserId, toolName,
                        "policy_disabled", startTime, summarize(input));
                record(decision, "BLOCK", 0, null, decision.reason());
                return decision;
            }

            if (usesKnowledgeBaseScope(toolName)) {
                Set<Long> allowedKbIds = resolveAllowedKbIds(client, policy);
                Set<Long> requestedKbIds = extractKbIds(input);
                if (!allowedKbIds.isEmpty()) {
                    if (requestedKbIds.isEmpty()) {
                        Decision decision = Decision.denied(requestId, effectiveClientId, effectiveUserId, toolName,
                                "kb_scope_required", startTime, summarize(input));
                        record(decision, "BLOCK", 0, null, decision.reason());
                        return decision;
                    }
                    if (!allowedKbIds.containsAll(requestedKbIds)) {
                        Decision decision = Decision.denied(requestId, effectiveClientId, effectiveUserId, toolName,
                                "kb_scope_denied", startTime, summarize(input));
                        record(decision, "BLOCK", 0, null, decision.reason());
                        return decision;
                    }
                }
            }

            int limit = resolveRateLimit(client, policy);
            if (limit > 0 && !allowByRateLimit(effectiveClientId, toolName, limit)) {
                Decision decision = Decision.denied(requestId, effectiveClientId, effectiveUserId, toolName,
                        "rate_limited_" + limit + "_per_minute", startTime, summarize(input));
                record(decision, "BLOCK", 0, null, decision.reason());
                return decision;
            }

            return Decision.allowed(requestId, effectiveClientId, effectiveUserId, toolName, startTime, summarize(input));
        } catch (Exception ex) {
            log.debug("[McpGovernance] fallback allow tool={} client={} error={}", toolName, effectiveClientId, ex.getMessage());
            return Decision.allowed(requestId, effectiveClientId, effectiveUserId, toolName, startTime, summarize(input));
        }
    }

    public void recordResult(Decision decision, String status, Object output, String reason) {
        if (decision == null) {
            return;
        }
        long latency = Math.max(0, System.currentTimeMillis() - decision.startTimeMs());
        record(decision, status, latency, output, reason);
    }

    public List<McpClient> listClients() {
        return clientMapper.selectList(new LambdaQueryWrapper<McpClient>().orderByAsc(McpClient::getClientId));
    }

    public List<McpToolPolicy> listPolicies(String clientId) {
        return policyMapper.selectList(new LambdaQueryWrapper<McpToolPolicy>()
                .eq(clientId != null && !clientId.isBlank(), McpToolPolicy::getClientId, clientId)
                .orderByAsc(McpToolPolicy::getClientId)
                .orderByAsc(McpToolPolicy::getToolName));
    }

    public List<McpAuditLog> listAudits(int limit) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<McpAuditLog>()
                .orderByDesc(McpAuditLog::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    public McpClient saveClient(McpClient client) {
        McpClient existing = findClient(client.getClientId());
        if (existing == null) {
            if (client.getStatus() == null) client.setStatus("active");
            if (client.getDefaultRateLimitPerMinute() == null) client.setDefaultRateLimitPerMinute(60);
            clientMapper.insert(client);
            return client;
        }
        existing.setDisplayName(client.getDisplayName());
        existing.setStatus(client.getStatus());
        existing.setDefaultRateLimitPerMinute(client.getDefaultRateLimitPerMinute());
        existing.setAllowedKbIds(client.getAllowedKbIds());
        existing.setDescription(client.getDescription());
        clientMapper.updateById(existing);
        return existing;
    }

    public McpToolPolicy savePolicy(McpToolPolicy policy) {
        McpToolPolicy existing = findPolicy(policy.getClientId(), policy.getToolName());
        if (existing == null) {
            if (policy.getEnabled() == null) policy.setEnabled(1);
            if (policy.getRateLimitPerMinute() == null) policy.setRateLimitPerMinute(60);
            policyMapper.insert(policy);
            return policy;
        }
        existing.setEnabled(policy.getEnabled());
        existing.setRateLimitPerMinute(policy.getRateLimitPerMinute());
        existing.setKbScopeJson(policy.getKbScopeJson());
        existing.setDescription(policy.getDescription());
        policyMapper.updateById(existing);
        return existing;
    }

    private McpToolRegistry findTool(String toolName) {
        try {
            return toolRegistryMapper.selectOne(new LambdaQueryWrapper<McpToolRegistry>()
                    .eq(McpToolRegistry::getName, toolName)
                    .last("LIMIT 1"));
        } catch (Exception ex) {
            return null;
        }
    }

    private McpClient findClient(String clientId) {
        try {
            return clientMapper.selectOne(new LambdaQueryWrapper<McpClient>()
                    .eq(McpClient::getClientId, clientId)
                    .last("LIMIT 1"));
        } catch (Exception ex) {
            return null;
        }
    }

    private McpToolPolicy findPolicy(String clientId, String toolName) {
        try {
            return policyMapper.selectOne(new LambdaQueryWrapper<McpToolPolicy>()
                    .eq(McpToolPolicy::getClientId, clientId)
                    .eq(McpToolPolicy::getToolName, toolName)
                    .last("LIMIT 1"));
        } catch (Exception ex) {
            return null;
        }
    }

    private int resolveRateLimit(McpClient client, McpToolPolicy policy) {
        if (policy != null && policy.getRateLimitPerMinute() != null) {
            return policy.getRateLimitPerMinute();
        }
        if (client != null && client.getDefaultRateLimitPerMinute() != null) {
            return client.getDefaultRateLimitPerMinute();
        }
        return 60;
    }

    private Set<Long> resolveAllowedKbIds(McpClient client, McpToolPolicy policy) {
        if (policy != null && policy.getKbScopeJson() != null && !policy.getKbScopeJson().isBlank()) {
            return parseIds(policy.getKbScopeJson());
        }
        if (client != null && client.getAllowedKbIds() != null && !client.getAllowedKbIds().isBlank()) {
            return parseIds(client.getAllowedKbIds());
        }
        return Set.of();
    }

    private Set<Long> extractKbIds(Object input) {
        if (input instanceof Map<?, ?> map) {
            return parseIds(map.get("kbIds"));
        }
        return Set.of();
    }

    private Set<Long> parseIds(Object value) {
        if (value == null) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                ids.addAll(parseIds(item));
            }
            return ids;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                ids.addAll(parseIds(Array.get(value, i)));
            }
            return ids;
        }
        if (value instanceof Number number) {
            ids.add(number.longValue());
            return ids;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(String.valueOf(value));
        while (matcher.find()) {
            ids.add(Long.parseLong(matcher.group()));
        }
        return ids;
    }

    private boolean usesKnowledgeBaseScope(String toolName) {
        return "doc_search".equals(toolName) || "keyword_search".equals(toolName);
    }

    private boolean allowByRateLimit(String clientId, String toolName, int limit) {
        long minute = System.currentTimeMillis() / 60_000;
        String key = clientId + ":" + toolName + ":" + minute;
        RateWindow window = rateWindows.computeIfAbsent(key, ignored -> new RateWindow(minute));
        cleanupOldWindows(minute);
        return window.count().incrementAndGet() <= limit;
    }

    private void cleanupOldWindows(long currentMinute) {
        rateWindows.entrySet().removeIf(entry -> entry.getValue().minute() < currentMinute - 2);
    }

    private void record(Decision decision, String status, long latencyMs, Object output, String reason) {
        try {
            McpAuditLog log = new McpAuditLog();
            log.setRequestId(decision.requestId());
            log.setClientId(decision.clientId());
            log.setUserId(decision.userId());
            log.setToolName(decision.toolName());
            log.setAction("CALL");
            log.setStatus(status);
            log.setLatencyMs((int) Math.min(Integer.MAX_VALUE, latencyMs));
            log.setReason(reason);
            log.setInputSummary(decision.inputSummary());
            log.setOutputSummary(summarize(output));
            log.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(log);
        } catch (Exception ex) {
            log.debug("[McpGovernance] skip audit persistence: {}", ex.getMessage());
        }
    }

    private String normalizeClientId(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        return AgentToolContext.isActive() ? INTERNAL_AGENT_CLIENT : PUBLIC_MCP_CLIENT;
    }

    private String currentUserId() {
        return AgentToolContext.isActive() ? AgentToolContext.get().getUserId() : "";
    }

    private String summarize(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value)
                .replaceAll("(?i)(api[_-]?key|cookie|password|secret)\\s*[:=]\\s*[^,}\\s]+", "$1=***")
                .replaceAll("(?i)(authorization)\\s*[:=]\\s*(?:Bearer\\s+)?[^,}\\s]+", "$1=***")
                .replace('\n', ' ');
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    private record RateWindow(long minute, AtomicInteger count) {
        RateWindow(long minute) {
            this(minute, new AtomicInteger());
        }
    }

    public record Decision(String requestId, String clientId, String userId, String toolName,
                           boolean allowed, String reason, long startTimeMs, String inputSummary) {
        static Decision allowed(String requestId, String clientId, String userId, String toolName,
                                long startTimeMs, String inputSummary) {
            return new Decision(requestId, clientId, userId, toolName, true, "allowed", startTimeMs, inputSummary);
        }

        static Decision denied(String requestId, String clientId, String userId, String toolName,
                               String reason, long startTimeMs, String inputSummary) {
            return new Decision(requestId, clientId, userId, toolName, false, reason, startTimeMs, inputSummary);
        }
    }
}
