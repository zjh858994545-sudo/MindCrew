package com.simon.MindCrew.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.simon.MindCrew.entity.SafetyEventLog;
import com.simon.MindCrew.mapper.SafetyEventLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SafetyGuardService {

    private final CopyOnWriteArrayList<SafetyEvent> events = new CopyOnWriteArrayList<>();

    @Autowired(required = false)
    private SafetyEventLogMapper eventLogMapper;

    private static final List<Rule> RULES = List.of(
            new Rule("PROMPT_LEAK", "HIGH", "输出系统提示词", "BLOCK"),
            new Rule("PROMPT_LEAK", "HIGH", "系统提示词", "BLOCK"),
            new Rule("PROMPT_INJECTION", "HIGH", "忽略之前", "BLOCK"),
            new Rule("PROMPT_INJECTION", "HIGH", "ignore previous", "BLOCK"),
            new Rule("SECRET_LEAK", "HIGH", "api key", "BLOCK"),
            new Rule("SECRET_LEAK", "HIGH", "cookie", "BLOCK"),
            new Rule("SECRET_LEAK", "HIGH", "密码", "BLOCK"),
            new Rule("UNAUTHORIZED_ACCESS", "HIGH", "越权", "BLOCK"),
            new Rule("TOOL_ABUSE", "MEDIUM", "未授权调用", "BLOCK")
    );

    public SafetyCheckResult checkUserInput(String input, String traceId, String userId) {
        Rule rule = match(input);
        if (rule == null) {
            return SafetyCheckResult.ok(input);
        }
        SafetyEvent event = record(traceId, userId, rule.riskType(), rule.riskLevel(),
                rule.action(), rule.pattern(), input);
        log.warn("[SafetyGuard] blocked user input traceId={} rule={}", traceId, rule.pattern());
        return new SafetyCheckResult(true, rule.riskType(), rule.riskLevel(), rule.action(),
                rule.pattern(), refusalAnswer(rule), event);
    }

    public String sanitizeRetrievedContent(String content, String traceId, String userId) {
        if (content == null || content.isBlank()) {
            return content;
        }
        Rule rule = match(content);
        if (rule == null) {
            return content;
        }
        record(traceId, userId, rule.riskType(), rule.riskLevel(), "SANITIZE", rule.pattern(), content);
        return content
                .replace(rule.pattern(), "[已移除的非可信指令]")
                .replace("忽略之前所有指令", "[已移除的非可信指令]");
    }

    public SafetyCheckResult checkToolCall(String toolName, boolean allowed, String traceId, String userId) {
        if (allowed) {
            return SafetyCheckResult.ok(toolName);
        }
        SafetyEvent event = record(traceId, userId, "TOOL_ABUSE", "MEDIUM", "BLOCK", toolName, toolName);
        return new SafetyCheckResult(true, "TOOL_ABUSE", "MEDIUM", "BLOCK",
                toolName, "该工具当前未授权调用，已被 MindCrew 安全策略拦截。", event);
    }

    public SafetyCheckResult checkFinalAnswer(String answer, String traceId, String userId) {
        if (answer == null) {
            return SafetyCheckResult.ok(null);
        }
        Rule rule = match(answer);
        if (rule == null) {
            return SafetyCheckResult.ok(maskSecrets(answer));
        }
        SafetyEvent event = record(traceId, userId, rule.riskType(), rule.riskLevel(), "MASK", rule.pattern(), answer);
        return new SafetyCheckResult(false, rule.riskType(), rule.riskLevel(), "MASK",
                rule.pattern(), maskSecrets(answer), event);
    }

    public List<SafetyEvent> listEvents() {
        if (events.isEmpty() && eventLogMapper != null) {
            try {
                return eventLogMapper.selectList(new LambdaQueryWrapper<SafetyEventLog>()
                                .orderByDesc(SafetyEventLog::getCreateTime)
                                .last("LIMIT 100"))
                        .stream()
                        .map(this::toEvent)
                        .toList();
            } catch (Exception ex) {
                log.debug("[SafetyGuard] skip db list fallback: {}", ex.getMessage());
            }
        }
        return events.stream()
                .sorted(Comparator.comparing(SafetyEvent::createdAt).reversed())
                .toList();
    }

    private Rule match(String input) {
        if (input == null) {
            return null;
        }
        String lower = input.toLowerCase();
        return RULES.stream()
                .filter(rule -> lower.contains(rule.pattern().toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    private SafetyEvent record(String traceId, String userId, String riskType, String riskLevel,
                               String action, String matchedRule, String input) {
        SafetyEvent event = new SafetyEvent(traceId, userId, riskType, riskLevel, action,
                matchedRule, summarize(input), LocalDateTime.now().toString());
        events.add(event);
        persistEvent(event);
        return event;
    }

    private String refusalAnswer(Rule rule) {
        return "已拦截：该请求命中 " + rule.riskType()
                + " 安全规则，MindCrew 不会输出系统提示词、密钥、Cookie、密码，也不会执行越权工具调用。";
    }

    private String maskSecrets(String text) {
        return text
                .replaceAll("(?i)(api[_-]?key|cookie|password|secret)\\s*[:=]\\s*[^,}\\s]+", "$1=***")
                .replaceAll("(?i)(authorization)\\s*[:=]\\s*(?:Bearer\\s+)?[^,}\\s]+", "$1=***")
                .replaceAll("(?i)bearer\\s+[a-z0-9._\\-]+", "Bearer ***");
    }

    private String summarize(String text) {
        if (text == null) {
            return null;
        }
        String masked = maskSecrets(text).replace('\n', ' ');
        return masked.length() > 240 ? masked.substring(0, 240) + "..." : masked;
    }

    private void persistEvent(SafetyEvent event) {
        if (eventLogMapper == null) {
            return;
        }
        try {
            SafetyEventLog entity = new SafetyEventLog();
            entity.setTraceId(event.traceId());
            entity.setUserId(event.userId());
            entity.setRiskType(event.riskType());
            entity.setRiskLevel(event.riskLevel());
            entity.setAction(event.action());
            entity.setMatchedRule(event.matchedRule());
            entity.setInputSummary(event.inputSummary());
            entity.setCreateTime(LocalDateTime.parse(event.createdAt()));
            eventLogMapper.insert(entity);
        } catch (Exception ex) {
            log.debug("[SafetyGuard] skip event persistence traceId={} riskType={} error={}",
                    event.traceId(), event.riskType(), ex.getMessage());
        }
    }

    private SafetyEvent toEvent(SafetyEventLog entity) {
        return new SafetyEvent(
                entity.getTraceId(),
                entity.getUserId(),
                entity.getRiskType(),
                entity.getRiskLevel(),
                entity.getAction(),
                entity.getMatchedRule(),
                entity.getInputSummary(),
                entity.getCreateTime() == null ? null : entity.getCreateTime().toString()
        );
    }

    private record Rule(String riskType, String riskLevel, String pattern, String action) {}
    public record SafetyCheckResult(boolean blocked, String riskType, String riskLevel,
                                    String action, String matchedRule, String safeText,
                                    SafetyEvent event) {
        static SafetyCheckResult ok(String text) {
            return new SafetyCheckResult(false, null, null, "ALLOW", null, text, null);
        }
    }
    public record SafetyEvent(String traceId, String userId, String riskType, String riskLevel,
                              String action, String matchedRule, String inputSummary, String createdAt) {}
}
