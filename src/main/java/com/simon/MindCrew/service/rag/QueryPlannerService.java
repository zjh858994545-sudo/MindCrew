package com.simon.MindCrew.service.rag;

import com.simon.MindCrew.agent.QueryRouter;
import com.simon.MindCrew.config.AiConfigHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPlannerService {

    private final QueryRouter queryRouter;
    private final AiConfigHolder aiConfigHolder;

    public QueryPlan plan(String originalQuery, String rewrittenQuery, String userId, List<Long> kbIds) {
        String primaryQuery = notBlank(rewrittenQuery) ? rewrittenQuery.trim() : safe(originalQuery);
        QueryRouter.IntentResult intent = queryRouter.route(primaryQuery);
        List<String> variants = buildVariants(originalQuery, primaryQuery, intent.getIntentType());
        String hyde = buildHyde(primaryQuery, intent.getIntentType());
        RetryPolicy retryPolicy = buildRetryPolicy(intent.getIntentType());

        QueryPlan plan = new QueryPlan(
                intent.getIntentType(),
                intent.getConfidence(),
                primaryQuery,
                variants,
                hyde,
                intent.getTools(),
                retryPolicy
        );
        log.info("[QueryPlanner] intent={} confidence={} variants={} retry={}",
                plan.intentType(), plan.intentConfidence(), plan.queryVariants().size(), plan.retryPolicy().enabled());
        return plan;
    }

    public RetrievalQuality assessRetrieval(QueryPlan plan, List<RetrievedChunk> rerankedChunks) {
        RetryPolicy policy = plan.retryPolicy();
        int count = rerankedChunks == null ? 0 : rerankedChunks.size();
        double topScore = topScore(rerankedChunks);
        long sourceCount = sourceCount(rerankedChunks);

        List<String> reasons = new ArrayList<>();
        if (count < policy.minChunkCount()) {
            reasons.add("chunk_count_below_" + policy.minChunkCount());
        }
        if (topScore < policy.minTopScore()) {
            reasons.add("top_score_below_" + policy.minTopScore());
        }
        if (count > 0 && sourceCount == 0) {
            reasons.add("missing_source");
        }
        boolean lowConfidence = policy.enabled() && !reasons.isEmpty();
        return new RetrievalQuality(count, topScore, sourceCount, lowConfidence, reasons);
    }

    public List<String> retryQueries(QueryPlan plan) {
        LinkedHashSet<String> queries = new LinkedHashSet<>(plan.queryVariants());
        if (plan.retryPolicy().useHydeOnRetry() && notBlank(plan.hydeDocument())) {
            queries.add(plan.hydeDocument());
        }
        return queries.stream().limit(5).toList();
    }

    private List<String> buildVariants(String originalQuery, String primaryQuery, String intentType) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        addIfUseful(variants, primaryQuery);
        addIfUseful(variants, originalQuery);

        switch (intentType) {
            case QueryRouter.EXACT_SEARCH -> {
                addIfUseful(variants, primaryQuery + " 条款 编号 规则 原文");
                addIfUseful(variants, primaryQuery + " 精确匹配 关键词");
            }
            case QueryRouter.REALTIME -> {
                addIfUseful(variants, primaryQuery + " 最新 近期 当前");
                addIfUseful(variants, primaryQuery + " 官方公告 更新");
            }
            case QueryRouter.COMPOUND -> {
                addIfUseful(variants, primaryQuery + " 对比 区别 优缺点");
                addIfUseful(variants, primaryQuery + " 方案差异 适用场景");
            }
            case QueryRouter.FOLLOWUP -> addIfUseful(variants, primaryQuery + " 结合上下文 继续说明");
            default -> {
                addIfUseful(variants, primaryQuery + " 背景 步骤 指标");
                addIfUseful(variants, primaryQuery + " 原因 方案 风险");
            }
        }
        return variants.stream().limit(4).toList();
    }

    private String buildHyde(String primaryQuery, String intentType) {
        if (QueryRouter.EXACT_SEARCH.equals(intentType)) {
            return primaryQuery + " 相关原文条款、编号、定义、适用范围、约束条件。";
        }
        try {
            String prompt = """
                    你是企业知识库检索增强器。请根据问题写一段“可能存在于知识库中的假设性答案文档”，用于 HyDE 检索。
                    要求：只输出一段中文，不要编造具体数字，不要输出免责声明。
                    问题：%s
                    """.formatted(primaryQuery);
            String text = aiConfigHolder.getChatModel().call(prompt).trim();
            if (text.length() > 500) {
                text = text.substring(0, 500);
            }
            return text;
        } catch (Exception ex) {
            log.debug("[QueryPlanner] HyDE generation fallback: {}", ex.getMessage());
            return "假设性知识库文档：" + primaryQuery + "。相关内容通常包括背景、定义、关键步骤、适用条件、风险点、指标和处理建议。";
        }
    }

    private RetryPolicy buildRetryPolicy(String intentType) {
        return switch (intentType) {
            case QueryRouter.EXACT_SEARCH -> new RetryPolicy(true, 3, 0.12, 1, 30, true, false, false);
            case QueryRouter.REALTIME -> new RetryPolicy(true, 3, 0.16, 1, 30, true, true, true);
            case QueryRouter.COMPOUND -> new RetryPolicy(true, 4, 0.16, 1, 30, true, true, true);
            case QueryRouter.FOLLOWUP -> new RetryPolicy(true, 2, 0.12, 1, 24, true, false, true);
            default -> new RetryPolicy(true, 3, 0.14, 1, 24, true, false, true);
        };
    }

    private double topScore(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0.0;
        }
        return chunks.stream()
                .mapToDouble(chunk -> chunk.getRerankScore() > 0 ? chunk.getRerankScore() : chunk.getScore())
                .max()
                .orElse(0.0);
    }

    private long sourceCount(List<RetrievedChunk> chunks) {
        if (chunks == null) {
            return 0;
        }
        Set<String> sources = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            if (chunk.getKnowledgeBaseId() != null) {
                sources.add("kb:" + chunk.getKnowledgeBaseId());
            } else if (notBlank(chunk.getSourceName())) {
                sources.add(chunk.getSourceName().toLowerCase(Locale.ROOT));
            } else if (chunk.getSource() != null) {
                sources.add(chunk.getSource().name());
            }
        }
        return sources.size();
    }

    private void addIfUseful(Set<String> out, String value) {
        if (notBlank(value)) {
            out.add(value.trim());
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record QueryPlan(String intentType,
                            double intentConfidence,
                            String primaryQuery,
                            List<String> queryVariants,
                            String hydeDocument,
                            List<String> tools,
                            RetryPolicy retryPolicy) {}

    public record RetryPolicy(boolean enabled,
                              int minChunkCount,
                              double minTopScore,
                              int maxAttempts,
                              int expandedTopK,
                              boolean useMultiQueryOnRetry,
                              boolean enableWebSearchOnRetry,
                              boolean useHydeOnRetry) {}

    public record RetrievalQuality(int chunkCount,
                                   double topScore,
                                   long sourceCount,
                                   boolean lowConfidence,
                                   List<String> reasons) {}
}
