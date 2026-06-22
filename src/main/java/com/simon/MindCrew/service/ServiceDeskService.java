package com.simon.MindCrew.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.simon.MindCrew.agent.MindCrewAgent;
import com.simon.MindCrew.controller.CollectingEmitter;
import com.simon.MindCrew.entity.KbKnowledgeBase;
import com.simon.MindCrew.entity.QaMessage;
import com.simon.MindCrew.entity.ServiceKnowledgeGap;
import com.simon.MindCrew.entity.ServiceTicket;
import com.simon.MindCrew.entity.ServiceTicketEvent;
import com.simon.MindCrew.mapper.KbKnowledgeBaseMapper;
import com.simon.MindCrew.mapper.QaMessageMapper;
import com.simon.MindCrew.mapper.ServiceKnowledgeGapMapper;
import com.simon.MindCrew.mapper.ServiceTicketEventMapper;
import com.simon.MindCrew.mapper.ServiceTicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceDeskService {

    public static final String STATUS_NEW = "new";
    public static final String STATUS_AI_DRAFTED = "ai_drafted";
    public static final String STATUS_NEEDS_REVIEW = "needs_review";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    public static final String FEEDBACK_NONE = "none";
    public static final String FEEDBACK_PENDING_REVIEW = "pending_human_review";
    public static final String FEEDBACK_GOLDEN_CANDIDATE = "golden_pair_candidate";
    public static final String FEEDBACK_GOLDEN_SYNCED = "golden_pair_synced";
    public static final String FEEDBACK_KB_GAP = "kb_gap";

    public static final String GAP_STATUS_OPEN = "open";

    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.70");
    private static final String SERVICE_DESK_KB_CATEGORY = "service_desk";

    private final ServiceTicketMapper ticketMapper;
    private final ServiceTicketEventMapper eventMapper;
    private final MindCrewAgent mindCrewAgent;
    private final QaMessageMapper qaMessageMapper;
    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final QaGoldenPairService qaGoldenPairService;
    private final ServiceKnowledgeGapMapper knowledgeGapMapper;
    private final RagEvalService ragEvalService;

    public IPage<ServiceTicket> page(int current, int size, String status, String category, String keyword) {
        return ticketMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<ServiceTicket>()
                .eq(hasText(status), ServiceTicket::getStatus, status)
                .eq(hasText(category), ServiceTicket::getCategory, category)
                .and(hasText(keyword), w -> w.like(ServiceTicket::getTicketNo, keyword)
                        .or().like(ServiceTicket::getTitle, keyword)
                        .or().like(ServiceTicket::getQuestion, keyword)
                        .or().like(ServiceTicket::getRequester, keyword))
                .orderByDesc(ServiceTicket::getCreateTime));
    }

    public ServiceTicket getById(Long id) {
        return ticketMapper.selectById(id);
    }

    public List<ServiceTicketEvent> events(Long ticketId) {
        return eventMapper.selectList(new LambdaQueryWrapper<ServiceTicketEvent>()
                .eq(ServiceTicketEvent::getTicketId, ticketId)
                .orderByAsc(ServiceTicketEvent::getCreateTime)
                .orderByAsc(ServiceTicketEvent::getId));
    }

    public List<ServiceKnowledgeGap> knowledgeGaps() {
        return knowledgeGapMapper.selectList(new LambdaQueryWrapper<ServiceKnowledgeGap>()
                .eq(ServiceKnowledgeGap::getStatus, GAP_STATUS_OPEN)
                .orderByDesc(ServiceKnowledgeGap::getCreateTime)
                .last("LIMIT 50"));
    }

    @Transactional
    public Long create(CreateTicketCommand command, String actor) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setTicketNo(nextTicketNo());
        ticket.setTitle(command.title());
        ticket.setRequester(command.requester());
        ticket.setRequesterRole(command.requesterRole());
        ticket.setDepartment(command.department());
        ticket.setPriority(defaultIfBlank(command.priority(), "P2"));
        ticket.setChannel(defaultIfBlank(command.channel(), "web"));
        ticket.setStatus(STATUS_NEW);
        ticket.setCategory(defaultIfBlank(command.category(), "GENERAL").toUpperCase(Locale.ROOT));
        ticket.setQuestion(command.question());
        ticket.setExpectedOutcome(command.expectedOutcome());
        ticket.setKbScope(command.kbScope());
        ticket.setAccepted(0);
        ticket.setFeedbackStatus(FEEDBACK_NONE);
        ticketMapper.insert(ticket);
        appendEvent(ticket.getId(), "CREATED", actor, "Ticket created from service desk.");
        return ticket.getId();
    }

    public DraftResult generateDraft(Long ticketId, String userId) {
        ServiceTicket ticket = requireTicket(ticketId);
        String actor = actorFromUserId(userId);

        try {
            AgentDraftProfile profile = generateWithMindCrewAgent(ticket, userId);
            ServiceTicket updated = persistDraft(ticket, profile.answer(), profile.sources(),
                    profile.confidence(), profile.traceId(), actor, profile.eventDetail());
            log.info("[ServiceDesk] MindCrewAgent draft ticketId={} confidence={} status={} traceId={}",
                    ticketId, updated.getConfidence(), updated.getStatus(), updated.getAiTraceId());
            return new DraftResult(updated, profile.recommendation(), STATUS_NEEDS_REVIEW.equals(updated.getStatus()));
        } catch (Exception ex) {
            log.warn("[ServiceDesk] MindCrewAgent failed, fallback to local answer. ticketId={} err={}",
                    ticketId, ex.getMessage());
            AnswerProfile fallback = answerFor(ticket);
            ServiceTicket updated = persistDraft(ticket, fallback.answer(),
                    fallback.sources() + "\nAgent fallback reason: " + safeMessage(ex),
                    fallback.confidence(), "sd-fallback-" + UUID.randomUUID(), actor,
                    "MindCrewAgent failed and local service desk fallback was used.");
            appendEvent(ticket.getId(), "AGENT_FALLBACK", "MindCrew-Agent", safeMessage(ex));
            return new DraftResult(updated, fallback.recommendation(), STATUS_NEEDS_REVIEW.equals(updated.getStatus()));
        }
    }

    public ServiceTicket acceptDraft(Long ticketId, String finalAnswer, String actor) {
        ServiceTicket ticket = requireTicket(ticketId);
        String answer = hasText(finalAnswer) ? finalAnswer.trim() : ticket.getAnswerDraft();
        if (!hasText(answer)) {
            throw new IllegalArgumentException("answer draft is empty");
        }
        ticket.setStatus(STATUS_ACCEPTED);
        ticket.setAccepted(1);
        ticket.setFinalAnswer(answer);
        ticket.setFeedbackStatus(FEEDBACK_GOLDEN_CANDIDATE);
        ticket.setResolutionOwner(actor);
        ticket.setResolvedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        appendEvent(ticket.getId(), "ACCEPTED", actor,
                "Human accepted/revised the answer. It becomes a Golden Pair candidate.");
        syncGoldenPair(ticket, answer, actor);
        return ticket;
    }

    public ServiceTicket retryGoldenPairSync(Long ticketId, String actor) {
        ServiceTicket ticket = requireTicket(ticketId);
        if (!STATUS_ACCEPTED.equals(ticket.getStatus())) {
            throw new IllegalStateException("Only accepted service tickets can retry Golden Pair sync.");
        }
        if (ticket.getGoldenPairId() != null) {
            appendEvent(ticket.getId(), "GOLDEN_PAIR_RETRY_SKIPPED", actor,
                    "Ticket already synced to qa_golden_pair.id=" + ticket.getGoldenPairId() + ".");
            return ticket;
        }
        String answer = firstText(ticket.getFinalAnswer(), ticket.getAnswerDraft());
        if (!hasText(answer)) {
            throw new IllegalArgumentException("accepted answer is empty");
        }
        appendEvent(ticket.getId(), "GOLDEN_PAIR_RETRY", actor,
                "Retry syncing accepted service desk answer to Golden Pair.");
        syncGoldenPair(ticket, answer, actor);
        return ticket;
    }

    @Transactional
    public ServiceTicket rejectDraft(Long ticketId, String reason, String actor) {
        ServiceTicket ticket = requireTicket(ticketId);
        String gapReason = hasText(reason) ? reason.trim() : "Rejected because the draft needs knowledge base update.";
        ticket.setStatus(STATUS_REJECTED);
        ticket.setAccepted(0);
        ticket.setFeedbackStatus(FEEDBACK_KB_GAP);
        ticket.setResolutionOwner(actor);
        ticket.setResolvedAt(LocalDateTime.now());
        ticketMapper.updateById(ticket);
        appendEvent(ticket.getId(), "REJECTED", actor, gapReason);
        createKnowledgeGap(ticket, gapReason, actor);
        return ticket;
    }

    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        long total = count(null);
        long newCount = count(STATUS_NEW);
        long drafted = count(STATUS_AI_DRAFTED);
        long needsReview = count(STATUS_NEEDS_REVIEW);
        long accepted = count(STATUS_ACCEPTED);
        long rejected = count(STATUS_REJECTED);
        long goldenCandidates = ticketMapper.selectCount(new LambdaQueryWrapper<ServiceTicket>()
                .in(ServiceTicket::getFeedbackStatus, FEEDBACK_GOLDEN_CANDIDATE, FEEDBACK_GOLDEN_SYNCED));
        long goldenSynced = ticketMapper.selectCount(new LambdaQueryWrapper<ServiceTicket>()
                .eq(ServiceTicket::getFeedbackStatus, FEEDBACK_GOLDEN_SYNCED));
        long knowledgeGaps = knowledgeGapMapper.selectCount(new LambdaQueryWrapper<ServiceKnowledgeGap>()
                .eq(ServiceKnowledgeGap::getStatus, GAP_STATUS_OPEN));

        out.put("total", total);
        out.put("newCount", newCount);
        out.put("drafted", drafted);
        out.put("needsReview", needsReview);
        out.put("accepted", accepted);
        out.put("rejected", rejected);
        out.put("goldenCandidates", goldenCandidates);
        out.put("goldenSynced", goldenSynced);
        out.put("knowledgeGaps", knowledgeGaps);
        out.put("acceptanceRate", ratio(accepted, accepted + rejected));
        out.put("avgConfidence", avgConfidence());
        return out;
    }

    private void createKnowledgeGap(ServiceTicket ticket, String reason, String actor) {
        ServiceKnowledgeGap existing = knowledgeGapMapper.selectOne(new LambdaQueryWrapper<ServiceKnowledgeGap>()
                .eq(ServiceKnowledgeGap::getTicketId, ticket.getId())
                .eq(ServiceKnowledgeGap::getStatus, GAP_STATUS_OPEN)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setReason(reason);
            existing.setSourceSummary(ticket.getSourceSummary());
            existing.setOwner(actor);
            knowledgeGapMapper.updateById(existing);
            appendEvent(ticket.getId(), "KNOWLEDGE_GAP_UPDATED", "KnowledgeOps",
                    "Existing knowledge gap task updated: service_knowledge_gap.id=" + existing.getId() + ".");
            return;
        }

        ServiceKnowledgeGap gap = new ServiceKnowledgeGap();
        gap.setTicketId(ticket.getId());
        gap.setTicketNo(ticket.getTicketNo());
        gap.setTitle(ticket.getTitle());
        gap.setCategory(ticket.getCategory());
        gap.setPriority(ticket.getPriority());
        gap.setReason(reason);
        gap.setSourceSummary(ticket.getSourceSummary());
        gap.setStatus(GAP_STATUS_OPEN);
        gap.setOwner(actor);
        knowledgeGapMapper.insert(gap);
        appendEvent(ticket.getId(), "KNOWLEDGE_GAP_CREATED", "KnowledgeOps",
                "Knowledge gap task created: service_knowledge_gap.id=" + gap.getId() + ".");
    }

    private void syncGoldenPair(ServiceTicket ticket, String answer, String actor) {
        try {
            Long pairId = qaGoldenPairService.create(
                    ticket.getQuestion(),
                    answer,
                    buildGoldenPairSourcesJson(ticket),
                    actorId(actor),
                    null
            );
            ticket.setGoldenPairId(pairId);
            ticket.setFeedbackStatus(FEEDBACK_GOLDEN_SYNCED);
            ticketMapper.updateById(ticket);
            appendEvent(ticket.getId(), "GOLDEN_PAIR_SYNCED", "GoldenPairService",
                    "Accepted service desk answer synced to qa_golden_pair.id=" + pairId + ".");
            syncRagEvalCase(ticket, answer, pairId, actor);
        } catch (Exception ex) {
            ticket.setFeedbackStatus(FEEDBACK_GOLDEN_CANDIDATE);
            ticketMapper.updateById(ticket);
            appendEvent(ticket.getId(), "GOLDEN_PAIR_SYNC_FAILED", "GoldenPairService",
                    "Kept as Golden Pair candidate. Reason: " + safeMessage(ex));
            log.warn("[ServiceDesk] Golden Pair sync failed ticketId={} reason={}", ticket.getId(), ex.getMessage());
        }
    }

    private void syncRagEvalCase(ServiceTicket ticket, String answer, Long pairId, String actor) {
        try {
            Long caseId = ragEvalService.upsertServiceDeskGoldenPairCase(
                    pairId,
                    ticket.getQuestion(),
                    answer,
                    ticket.getCategory(),
                    ticket.getSourceSummary(),
                    actorId(actor)
            );
            if (caseId != null) {
                appendEvent(ticket.getId(), "RAG_EVAL_CASE_SYNCED", "RagEvalService",
                        "Accepted answer synced to rag_eval_case.id=" + caseId + ".");
            }
        } catch (Exception ex) {
            appendEvent(ticket.getId(), "RAG_EVAL_CASE_SYNC_FAILED", "RagEvalService",
                    "Golden Pair was synced, but RAG Eval case sync failed: " + safeMessage(ex));
            log.warn("[ServiceDesk] RAG Eval case sync failed ticketId={} pairId={} reason={}",
                    ticket.getId(), pairId, ex.getMessage());
        }
    }

    private AgentDraftProfile generateWithMindCrewAgent(ServiceTicket ticket, String userId) {
        List<Long> kbIds = resolveServiceDeskKbIds();
        CollectingEmitter collector = new CollectingEmitter();
        Long conversationId = mindCrewAgent.execute(normalizeAgentUserId(userId), null,
                buildAgentQuestion(ticket), kbIds, List.of(), collector);
        if (conversationId == null && collector.getConversationId() != null) {
            conversationId = collector.getConversationId();
        }

        QaMessage assistantMessage = latestAssistantMessage(conversationId);
        String answer = firstText(assistantMessage == null ? null : assistantMessage.getContent(),
                collector.getAnswer());
        if (!hasText(answer)) {
            throw new IllegalStateException("MindCrewAgent returned empty answer");
        }

        String sourcesJson = firstText(assistantMessage == null ? null : assistantMessage.getSources(),
                JSON.toJSONString(collector.getSources()));
        Map<String, Object> retrievalLog = parseMap(firstText(
                assistantMessage == null ? null : assistantMessage.getRetrievalLog(),
                JSON.toJSONString(collector.getRetrievalLog())));
        String traceId = firstText(collector.getTraceId(),
                collector.getDonePayload().get("traceId") == null ? null : String.valueOf(collector.getDonePayload().get("traceId")),
                conversationId == null ? null : "conversation-" + conversationId);

        BigDecimal confidence = estimateAgentConfidence(collector, retrievalLog, sourcesJson, answer);
        String sourceSummary = buildSourceSummary(conversationId, traceId, kbIds, sourcesJson, retrievalLog, collector);
        String recommendation = buildAgentRecommendation(confidence, collector, retrievalLog, sourcesJson);
        String detail = "MindCrewAgent executed. " + compactEventDetail(conversationId, traceId, kbIds, retrievalLog, collector);
        return new AgentDraftProfile(answer, sourceSummary, confidence, recommendation, traceId, detail);
    }

    private ServiceTicket persistDraft(ServiceTicket ticket,
                                       String answer,
                                       String sourceSummary,
                                       BigDecimal confidence,
                                       String traceId,
                                       String actor,
                                       String eventDetail) {
        String nextStatus = confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0
                ? STATUS_NEEDS_REVIEW
                : STATUS_AI_DRAFTED;
        ticket.setStatus(nextStatus);
        ticket.setConfidence(confidence);
        ticket.setAnswerDraft(answer);
        ticket.setSourceSummary(sourceSummary);
        ticket.setAiTraceId(traceId);
        ticket.setFeedbackStatus(FEEDBACK_PENDING_REVIEW);
        ticketMapper.updateById(ticket);

        String eventType = STATUS_NEEDS_REVIEW.equals(nextStatus) ? "NEEDS_REVIEW" : "AI_DRAFTED";
        appendEvent(ticket.getId(), eventType, "MindCrew-Agent", eventDetail);
        if (STATUS_NEEDS_REVIEW.equals(nextStatus)) {
            appendEvent(ticket.getId(), "LOW_CONFIDENCE", "QueryPlanner",
                    "Confidence " + confidence + " is below " + LOW_CONFIDENCE_THRESHOLD + "; human review required.");
        }
        return ticket;
    }

    private String buildAgentQuestion(ServiceTicket ticket) {
        return """
                你是 MindCrew 企业知识服务台 Agent。请基于企业知识库检索结果回答业务工单，不能凭空编造制度。

                【工单编号】%s
                【标题】%s
                【知识域】%s
                【优先级】%s
                【申请人】%s / %s / %s
                【期望结果】%s
                【指定知识范围】%s

                【用户原始问题】
                %s

                请输出：
                1. 结论：能不能做、是否需要审批或人工复核。
                2. 处理步骤：给业务人员可以直接执行的步骤。
                3. 风险与审批：涉及权限、数据、金额、客户交付时必须写清楚。
                4. 引用依据：引用你检索到的制度/SOP/知识来源。
                5. 如果证据不足，请明确说明需要人工确认，不要强行给确定答案。
                """.formatted(
                nullToDash(ticket.getTicketNo()),
                nullToDash(ticket.getTitle()),
                nullToDash(ticket.getCategory()),
                nullToDash(ticket.getPriority()),
                nullToDash(ticket.getRequester()),
                nullToDash(ticket.getDepartment()),
                nullToDash(ticket.getRequesterRole()),
                nullToDash(ticket.getExpectedOutcome()),
                nullToDash(ticket.getKbScope()),
                nullToDash(ticket.getQuestion())
        );
    }

    private List<Long> resolveServiceDeskKbIds() {
        try {
            List<KbKnowledgeBase> kbs = kbKnowledgeBaseMapper.selectList(new QueryWrapper<KbKnowledgeBase>()
                    .eq("category", SERVICE_DESK_KB_CATEGORY)
                    .eq("status", "ready")
                    .eq("deleted", 0)
                    .select("id"));
            return kbs.stream().map(KbKnowledgeBase::getId).toList();
        } catch (Exception ex) {
            log.warn("[ServiceDesk] resolve service desk kb failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private QaMessage latestAssistantMessage(Long conversationId) {
        if (conversationId == null) return null;
        return qaMessageMapper.selectOne(new LambdaQueryWrapper<QaMessage>()
                .eq(QaMessage::getConversationId, conversationId)
                .eq(QaMessage::getRole, "assistant")
                .orderByDesc(QaMessage::getId)
                .last("LIMIT 1"));
    }

    private BigDecimal estimateAgentConfidence(CollectingEmitter collector,
                                               Map<String, Object> retrievalLog,
                                               String sourcesJson,
                                               String answer) {
        double score = 0.64d;
        int sourceCount = sourceCount(sourcesJson);
        if (sourceCount >= 3) score += 0.16d;
        else if (sourceCount > 0) score += 0.08d;
        else score -= 0.12d;

        if (answer != null && answer.length() >= 300) score += 0.04d;
        if (collector.isFallback()) score -= 0.08d;
        if (collector.isEmergency()) score -= 0.05d;
        if (Boolean.TRUE.equals(collector.getReflectionPassed())) score += 0.03d;
        if (Boolean.FALSE.equals(collector.getReflectionPassed())) score -= 0.08d;
        if (!collector.getErrors().isEmpty()) score -= 0.12d;

        Object queryPlan = retrievalLog.get("queryPlan");
        if (queryPlan instanceof Map<?, ?> map) {
            Object confidence = map.get("intentConfidence");
            if (confidence instanceof Number number) {
                score += Math.min(0.08d, number.doubleValue() * 0.08d);
            }
        }
        Object retryCount = retrievalLog.get("retrievalRetryCount");
        if (retryCount instanceof Number number && number.intValue() > 0) {
            score -= 0.03d;
        }
        score = Math.max(0.45d, Math.min(0.94d, score));
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildSourceSummary(Long conversationId,
                                      String traceId,
                                      List<Long> kbIds,
                                      String sourcesJson,
                                      Map<String, Object> retrievalLog,
                                      CollectingEmitter collector) {
        List<String> lines = new ArrayList<>();
        lines.add("MindCrewAgent + QueryPlanner + RAG");
        lines.add("conversationId=" + (conversationId == null ? "-" : conversationId));
        lines.add("traceId=" + nullToDash(traceId));
        lines.add("kbIds=" + (kbIds.isEmpty() ? "all-accessible" : kbIds));
        String intent = firstText(collector.getIntentType(), stringValue(retrievalLog.get("intentType")), "-");
        lines.add("intent=" + intent);
        lines.add("retrieval=" + retrievalStats(retrievalLog));
        lines.add("sources:");
        lines.addAll(sourceLines(sourcesJson));
        if (!collector.getErrors().isEmpty()) {
            lines.add("errors=" + collector.getErrors());
        }
        return String.join("\n", lines);
    }

    private String buildGoldenPairSourcesJson(ServiceTicket ticket) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "service_ticket");
        source.put("ticketId", ticket.getId());
        source.put("ticketNo", nullToDash(ticket.getTicketNo()));
        source.put("category", nullToDash(ticket.getCategory()));
        source.put("traceId", nullToDash(ticket.getAiTraceId()));
        source.put("sourceSummary", nullToDash(ticket.getSourceSummary()));
        return JSON.toJSONString(List.of(source));
    }

    private String buildAgentRecommendation(BigDecimal confidence,
                                            CollectingEmitter collector,
                                            Map<String, Object> retrievalLog,
                                            String sourcesJson) {
        if (confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0) {
            return "MindCrewAgent 已完成 QueryPlanner 与 RAG 检索，但证据强度不足，建议人工复核后再回复业务方。"
                    + " 来源数=" + sourceCount(sourcesJson) + "，" + retrievalStats(retrievalLog);
        }
        if (collector.isFallback()) {
            return "Agent 触发了 fallback，建议审核员确认制度依据后再采纳。";
        }
        return "MindCrewAgent 已基于知识库生成草稿，可由审核员快速确认并沉淀为 Golden Pair 候选样本。";
    }

    private String compactEventDetail(Long conversationId,
                                      String traceId,
                                      List<Long> kbIds,
                                      Map<String, Object> retrievalLog,
                                      CollectingEmitter collector) {
        return "conversationId=" + (conversationId == null ? "-" : conversationId)
                + ", traceId=" + nullToDash(traceId)
                + ", intent=" + firstText(collector.getIntentType(), stringValue(retrievalLog.get("intentType")), "-")
                + ", kbIds=" + (kbIds.isEmpty() ? "all-accessible" : kbIds)
                + ", " + retrievalStats(retrievalLog);
    }

    private List<String> sourceLines(String sourcesJson) {
        if (!hasText(sourcesJson)) return List.of("- no explicit source returned");
        try {
            JSONArray array = JSON.parseArray(sourcesJson);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < Math.min(5, array.size()); i++) {
                JSONObject source = array.getJSONObject(i);
                String name = firstText(source.getString("name"), source.getString("source"), source.getString("type"), "source");
                String chapter = firstText(source.getString("chapter"), "");
                String page = source.get("pageNumber") == null ? "" : " p." + source.get("pageNumber");
                lines.add("- " + name + (hasText(chapter) ? " / " + chapter : "") + page);
            }
            return lines.isEmpty() ? List.of("- no explicit source returned") : lines;
        } catch (Exception ex) {
            return List.of("- " + sourcesJson);
        }
    }

    private String retrievalStats(Map<String, Object> log) {
        if (log == null || log.isEmpty()) return "retrievalLog=empty";
        return "vector=" + nullToDash(log.get("vectorResults"))
                + ", bm25=" + nullToDash(log.get("bm25Results"))
                + ", web=" + nullToDash(log.get("webResults"))
                + ", rrf=" + nullToDash(log.get("rrfCount"))
                + ", rerankTop=" + nullToDash(log.get("rerankTop"))
                + ", retry=" + nullToDash(log.get("retrievalRetryCount"));
    }

    private int sourceCount(String sourcesJson) {
        if (!hasText(sourcesJson)) return 0;
        try {
            return JSON.parseArray(sourcesJson).size();
        } catch (Exception ex) {
            return 0;
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (!hasText(json)) return new LinkedHashMap<>();
        try {
            JSONObject object = JSON.parseObject(json);
            return new LinkedHashMap<>(object);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private ServiceTicket requireTicket(Long ticketId) {
        ServiceTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("service ticket not found: " + ticketId);
        }
        return ticket;
    }

    private void appendEvent(Long ticketId, String eventType, String actor, String detail) {
        ServiceTicketEvent event = new ServiceTicketEvent();
        event.setTicketId(ticketId);
        event.setEventType(eventType);
        event.setActor(defaultIfBlank(actor, "system"));
        event.setDetail(detail);
        eventMapper.insert(event);
    }

    private long count(String status) {
        LambdaQueryWrapper<ServiceTicket> wrapper = new LambdaQueryWrapper<>();
        if (hasText(status)) {
            wrapper.eq(ServiceTicket::getStatus, status);
        }
        return ticketMapper.selectCount(wrapper);
    }

    private BigDecimal avgConfidence() {
        List<ServiceTicket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<ServiceTicket>()
                .isNotNull(ServiceTicket::getConfidence));
        if (tickets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (ServiceTicket ticket : tickets) {
            if (ticket.getConfidence() != null) {
                sum = sum.add(ticket.getConfidence());
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private AnswerProfile answerFor(ServiceTicket ticket) {
        String category = defaultIfBlank(ticket.getCategory(), "GENERAL").toUpperCase(Locale.ROOT);
        String question = defaultIfBlank(ticket.getQuestion(), "");
        String lower = question.toLowerCase(Locale.ROOT);
        return switch (category) {
            case "HR" -> new AnswerProfile(
                    "可以申请，但需要先确认你的年假额度是否已经按入职日期折算生成。\n\n"
                            + "处理建议：\n"
                            + "1. 在 OA-考勤-休假申请中选择年假，填写请假日期和交接人。\n"
                            + "2. 审批链路为直属上级 -> 部门负责人；连续 2 天以内通常不需要 HRBP 额外审批。\n"
                            + "3. 如果系统额度不足，可以先联系 HRBP 核对折算额度。\n"
                            + "4. 请假前需要同步交接事项，避免影响迭代排期。",
                    "HR-Handbook#AnnualLeave, Leave-SOP#ApprovalChain",
                    new BigDecimal("0.66"),
                    "Agent 不可用时启用本地兜底，建议人工复核后再采纳。");
            case "IT" -> new AnswerProfile(
                    "优先按 MFA 绑定问题处理，不建议直接重置账号。\n\n"
                            + "排查步骤：\n"
                            + "1. 确认新电脑时间与网络时间同步。\n"
                            + "2. 在账号中心重新绑定 MFA 设备后重试 VPN。\n"
                            + "3. 若仍失败，提交 IT 工单并附 VPN 报错截图、员工工号和设备编号。\n"
                            + "4. 后台临时访问需要直属主管确认。",
                    "VPN-MFA-Runbook#MFAReset, IT-Account-SOP#AccessRecovery",
                    new BigDecimal("0.66"),
                    "Agent 不可用时启用本地兜底，建议人工复核后再采纳。");
            case "FINANCE" -> new AnswerProfile(
                    "差旅报销需要保证发票抬头、税号和出行信息一致。\n\n"
                            + "1. 发票抬头填写公司工商全称，税号以财务系统最新主体为准。\n"
                            + "2. 高铁票、酒店发票、客户拜访记录和审批单需要一并上传。\n"
                            + "3. 差旅结束后 30 个自然日内提交报销。\n"
                            + "4. 发票主体或税号错误时需要联系商家重开。",
                    "Finance-Reimbursement-SOP#Invoice, Travel-Policy#Deadline",
                    new BigDecimal("0.66"),
                    "Agent 不可用时启用本地兜底，建议人工复核后再采纳。");
            case "SECURITY" -> securityAnswer(lower);
            case "SALES" -> new AnswerProfile(
                    "试点报价不能只按折扣处理，需要同时看账号数、周期、转年框承诺和 API 调用量。\n\n"
                            + "1. 3 个月试点可以使用试点价目表。\n"
                            + "2. 低于标准折扣线需要区域负责人审批。\n"
                            + "3. 合同中应写明转正条件和抵扣规则。\n"
                            + "4. 不建议口头承诺最低价，先输出正式报价单并走商务审批。",
                    "Sales-Pricing-Playbook#Pilot, Pilot-Contract-SOP#Approval",
                    new BigDecimal("0.66"),
                    "Agent 不可用时启用本地兜底，建议商务人工复核。");
            default -> new AnswerProfile(
                    "当前问题没有命中明确知识域，只能给出通用处理建议。\n\n"
                            + "建议补充业务背景、涉及系统、期望完成时间和风险等级；如果涉及客户数据、生产权限或合同金额，需要升级到对应业务负责人审核。",
                    "General-Service-Desk#Fallback",
                    new BigDecimal("0.55"),
                    "低置信度：建议补充知识库或转人工。");
        };
    }

    private AnswerProfile securityAnswer(String lowerQuestion) {
        if (lowerQuestion.contains("日志") || lowerQuestion.contains("手机号") || lowerQuestion.contains("用户")) {
            return new AnswerProfile(
                    "不可以直接通过邮件发送全量原始日志，尤其在包含手机号、用户 ID 等个人信息时。\n\n"
                            + "1. 先确认客户审计目的、字段范围、时间范围和接收人清单。\n"
                            + "2. 默认只提供最小必要字段，并对敏感字段做脱敏或哈希化。\n"
                            + "3. 需要走数据导出审批：业务负责人 -> 安全负责人 -> 法务/隐私合规。\n"
                            + "4. 交付方式应使用受控下载链接，设置有效期、水印和访问审计。",
                    "Security-Data-Export-Policy#MinimumNecessary, PII-Desensitization-SOP#Export",
                    new BigDecimal("0.66"),
                    "Agent 不可用时启用本地兜底，但安全类问题必须人工确认。");
        }
        return new AnswerProfile(
                "不建议给外包人员直接开通生产库只读权限。\n\n"
                        + "1. 优先由内部员工代查或通过脱敏查询平台提供结果。\n"
                        + "2. 如确需访问，必须限定工单编号、库表范围、时间窗口和只读权限。\n"
                        + "3. 访问需由项目负责人和安全负责人审批。\n"
                        + "4. 访问过程需要保留审计日志，到期自动回收权限。",
                "Security-Access-Policy#VendorAccess, Database-Access-SOP#ReadOnly",
                new BigDecimal("0.66"),
                "Agent 不可用时启用本地兜底，但安全类问题必须人工确认。");
    }

    private String nextTicketNo() {
        return "MC-SD-" + LocalDateTime.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String normalizeAgentUserId(String userId) {
        if (!hasText(userId)) return "0";
        String trimmed = userId.trim();
        if (trimmed.startsWith("user-")) return trimmed.substring("user-".length());
        return trimmed;
    }

    private static String actorFromUserId(String userId) {
        return "user-" + normalizeAgentUserId(userId);
    }

    private static Long actorId(String actor) {
        String normalized = normalizeAgentUserId(actor);
        try {
            return Long.parseLong(normalized);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) return "unknown error";
        return ex.getMessage().substring(0, Math.min(500, ex.getMessage().length()));
    }

    private static String nullToDash(Object value) {
        if (value == null) return "-";
        String text = String.valueOf(value);
        return text.isBlank() ? "-" : text;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return "";
    }

    public record CreateTicketCommand(String title,
                                      String requester,
                                      String requesterRole,
                                      String department,
                                      String priority,
                                      String channel,
                                      String category,
                                      String question,
                                      String expectedOutcome,
                                      String kbScope) {
    }

    public record DraftResult(ServiceTicket ticket, String recommendation, boolean lowConfidence) {
    }

    private record AnswerProfile(String answer, String sources, BigDecimal confidence, String recommendation) {
    }

    private record AgentDraftProfile(String answer,
                                     String sources,
                                     BigDecimal confidence,
                                     String recommendation,
                                     String traceId,
                                     String eventDetail) {
    }
}
