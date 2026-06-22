package com.simon.MindCrew.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.simon.MindCrew.agent.MindCrewAgent;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceDeskServiceTest {

    @Test
    void draftUsesMindCrewAgentAndRagMetadata() {
        Fixture fixture = fixture();
        ServiceTicket ticket = ticket(1L, "SECURITY", "Can we email raw audit logs to a customer?");
        when(fixture.ticketMapper.selectById(1L)).thenReturn(ticket);
        when(fixture.agent.execute(anyString(), isNull(), anyString(), anyList(), anyList(), any(SseEmitter.class)))
                .thenReturn(99L);
        when(fixture.messageMapper.selectOne(any(Wrapper.class))).thenReturn(agentMessage());

        ServiceDeskService.DraftResult result = fixture.service.generateDraft(1L, "7");

        assertEquals(ServiceDeskService.STATUS_AI_DRAFTED, result.ticket().getStatus());
        assertEquals(ServiceDeskService.FEEDBACK_PENDING_REVIEW, result.ticket().getFeedbackStatus());
        assertTrue(result.ticket().getAnswerDraft().contains("controlled download link"));
        assertTrue(result.ticket().getSourceSummary().contains("MindCrewAgent + QueryPlanner + RAG"));
        assertTrue(result.ticket().getConfidence().doubleValue() >= 0.70d);
        verify(fixture.agent).execute(anyString(), isNull(), anyString(), anyList(), anyList(), any(SseEmitter.class));
        verify(fixture.ticketMapper).updateById(any(ServiceTicket.class));
    }

    @Test
    void agentFailureFallsBackToHumanReview() {
        Fixture fixture = fixture();
        ServiceTicket ticket = ticket(2L, "GENERAL", "The knowledge base has no matching process.");
        when(fixture.ticketMapper.selectById(2L)).thenReturn(ticket);
        when(fixture.agent.execute(anyString(), isNull(), anyString(), anyList(), anyList(), any(SseEmitter.class)))
                .thenThrow(new RuntimeException("model unavailable"));

        ServiceDeskService.DraftResult result = fixture.service.generateDraft(2L, "7");

        assertTrue(result.lowConfidence());
        assertEquals(ServiceDeskService.STATUS_NEEDS_REVIEW, result.ticket().getStatus());
        assertTrue(result.ticket().getSourceSummary().contains("Agent fallback reason"));

        ArgumentCaptor<ServiceTicketEvent> eventCaptor = ArgumentCaptor.forClass(ServiceTicketEvent.class);
        verify(fixture.eventMapper, atLeastOnce()).insert(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(event -> "AGENT_FALLBACK".equals(event.getEventType())));
    }

    @Test
    void acceptingDraftSyncsGoldenPairWhenModelReady() {
        Fixture fixture = fixture();
        ServiceTicket ticket = ticket(3L, "HR", "Can probation employees request annual leave?");
        ticket.setAnswerDraft("draft answer");
        ticket.setSourceSummary("source summary");
        when(fixture.ticketMapper.selectById(3L)).thenReturn(ticket);
        when(fixture.goldenPairService.create(anyString(), anyString(), anyString(), anyLong(), isNull()))
                .thenReturn(88L);
        when(fixture.ragEvalService.upsertServiceDeskGoldenPairCase(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(188L);

        ServiceTicket accepted = fixture.service.acceptDraft(3L, "final answer", "auditor-1");

        assertEquals(ServiceDeskService.STATUS_ACCEPTED, accepted.getStatus());
        assertEquals(1, accepted.getAccepted());
        assertEquals("final answer", accepted.getFinalAnswer());
        assertEquals(ServiceDeskService.FEEDBACK_GOLDEN_SYNCED, accepted.getFeedbackStatus());
        assertEquals(88L, accepted.getGoldenPairId());
        assertNotNull(accepted.getResolvedAt());
        verify(fixture.goldenPairService).create(anyString(), anyString(), anyString(), anyLong(), isNull());
        verify(fixture.ragEvalService).upsertServiceDeskGoldenPairCase(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong());

        ArgumentCaptor<ServiceTicketEvent> eventCaptor = ArgumentCaptor.forClass(ServiceTicketEvent.class);
        verify(fixture.eventMapper, atLeastOnce()).insert(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(event -> "GOLDEN_PAIR_SYNCED".equals(event.getEventType())));
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(event -> "RAG_EVAL_CASE_SYNCED".equals(event.getEventType())));
    }

    @Test
    void acceptingDraftKeepsCandidateWhenGoldenPairSyncFails() {
        Fixture fixture = fixture();
        ServiceTicket ticket = ticket(4L, "HR", "Can probation employees request annual leave?");
        ticket.setAnswerDraft("draft answer");
        when(fixture.ticketMapper.selectById(4L)).thenReturn(ticket);
        when(fixture.goldenPairService.create(anyString(), anyString(), anyString(), anyLong(), isNull()))
                .thenThrow(new RuntimeException("embedding key missing"));

        ServiceTicket accepted = fixture.service.acceptDraft(4L, "final answer", "user-7");

        assertEquals(ServiceDeskService.STATUS_ACCEPTED, accepted.getStatus());
        assertEquals(ServiceDeskService.FEEDBACK_GOLDEN_CANDIDATE, accepted.getFeedbackStatus());
        assertNotNull(accepted.getResolvedAt());
        verify(fixture.ticketMapper, atLeastOnce()).updateById(any(ServiceTicket.class));

        ArgumentCaptor<ServiceTicketEvent> eventCaptor = ArgumentCaptor.forClass(ServiceTicketEvent.class);
        verify(fixture.eventMapper, atLeastOnce()).insert(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(event -> "GOLDEN_PAIR_SYNC_FAILED".equals(event.getEventType())));
    }

    @Test
    void retryGoldenPairSyncCanRecoverCandidate() {
        Fixture fixture = fixture();
        ServiceTicket ticket = ticket(5L, "SECURITY", "Can we export raw logs?");
        ticket.setStatus(ServiceDeskService.STATUS_ACCEPTED);
        ticket.setFeedbackStatus(ServiceDeskService.FEEDBACK_GOLDEN_CANDIDATE);
        ticket.setFinalAnswer("final accepted answer");
        when(fixture.ticketMapper.selectById(5L)).thenReturn(ticket);
        when(fixture.goldenPairService.create(anyString(), anyString(), anyString(), anyLong(), isNull()))
                .thenReturn(99L);
        when(fixture.ragEvalService.upsertServiceDeskGoldenPairCase(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(199L);

        ServiceTicket synced = fixture.service.retryGoldenPairSync(5L, "user-7");

        assertEquals(ServiceDeskService.FEEDBACK_GOLDEN_SYNCED, synced.getFeedbackStatus());
        assertEquals(99L, synced.getGoldenPairId());

        ArgumentCaptor<ServiceTicketEvent> eventCaptor = ArgumentCaptor.forClass(ServiceTicketEvent.class);
        verify(fixture.eventMapper, atLeastOnce()).insert(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(event -> "GOLDEN_PAIR_RETRY".equals(event.getEventType())));
    }

    @Test
    void rejectCreatesKnowledgeGapTask() {
        Fixture fixture = fixture();
        ServiceTicket ticket = ticket(6L, "SECURITY", "Vendor needs production DB access.");
        ticket.setAnswerDraft("draft answer");
        ticket.setSourceSummary("retrieval summary");
        when(fixture.ticketMapper.selectById(6L)).thenReturn(ticket);
        when(fixture.knowledgeGapMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        ServiceTicket rejected = fixture.service.rejectDraft(6L, "Missing latest access policy.", "user-7");

        assertEquals(ServiceDeskService.STATUS_REJECTED, rejected.getStatus());
        assertEquals(ServiceDeskService.FEEDBACK_KB_GAP, rejected.getFeedbackStatus());
        verify(fixture.knowledgeGapMapper).insert(any(ServiceKnowledgeGap.class));

        ArgumentCaptor<ServiceTicketEvent> eventCaptor = ArgumentCaptor.forClass(ServiceTicketEvent.class);
        verify(fixture.eventMapper, atLeastOnce()).insert(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(event -> "KNOWLEDGE_GAP_CREATED".equals(event.getEventType())));
    }

    private Fixture fixture() {
        ServiceTicketMapper ticketMapper = mock(ServiceTicketMapper.class);
        ServiceTicketEventMapper eventMapper = mock(ServiceTicketEventMapper.class);
        MindCrewAgent agent = mock(MindCrewAgent.class);
        QaMessageMapper messageMapper = mock(QaMessageMapper.class);
        KbKnowledgeBaseMapper kbKnowledgeBaseMapper = mock(KbKnowledgeBaseMapper.class);
        QaGoldenPairService goldenPairService = mock(QaGoldenPairService.class);
        ServiceKnowledgeGapMapper knowledgeGapMapper = mock(ServiceKnowledgeGapMapper.class);
        RagEvalService ragEvalService = mock(RagEvalService.class);

        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setId(10L);
        when(kbKnowledgeBaseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(kb));

        return new Fixture(
                new ServiceDeskService(ticketMapper, eventMapper, agent, messageMapper, kbKnowledgeBaseMapper,
                        goldenPairService, knowledgeGapMapper, ragEvalService),
                ticketMapper,
                eventMapper,
                agent,
                messageMapper,
                goldenPairService,
                knowledgeGapMapper,
                ragEvalService
        );
    }

    private QaMessage agentMessage() {
        QaMessage message = new QaMessage();
        message.setConversationId(99L);
        message.setRole("assistant");
        message.setContent("""
                Do not send full raw logs by email.
                Steps:
                1. Confirm audit purpose, fields, time range, and recipient.
                2. Export the minimum necessary fields and mask phone numbers, user IDs, and IP addresses.
                3. Route approval through business owner, security owner, legal, and privacy compliance.
                4. Deliver through a controlled download link with expiry, watermark, and access audit.
                """);
        message.setSources("""
                [
                  {"name":"MindCrew Service Desk Knowledge","chapter":"Security-Data-Export-Policy","score":0.91},
                  {"name":"MindCrew Service Desk Knowledge","chapter":"PII-Desensitization-SOP","score":0.88},
                  {"name":"MindCrew Service Desk Knowledge","chapter":"Security-Access-Policy","score":0.77}
                ]
                """);
        message.setRetrievalLog("""
                {
                  "intentType":"knowledge_query",
                  "vectorResults":1,
                  "bm25Results":3,
                  "webResults":0,
                  "rrfCount":4,
                  "rerankTop":3,
                  "retrievalRetryCount":0,
                  "queryPlan":{"intentConfidence":0.9}
                }
                """);
        return message;
    }

    private ServiceTicket ticket(Long id, String category, String question) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(id);
        ticket.setTicketNo("MC-SD-TEST-" + id);
        ticket.setTitle("test");
        ticket.setCategory(category);
        ticket.setQuestion(question);
        ticket.setStatus(ServiceDeskService.STATUS_NEW);
        ticket.setFeedbackStatus(ServiceDeskService.FEEDBACK_NONE);
        ticket.setAccepted(0);
        return ticket;
    }

    private record Fixture(ServiceDeskService service,
                           ServiceTicketMapper ticketMapper,
                           ServiceTicketEventMapper eventMapper,
                           MindCrewAgent agent,
                           QaMessageMapper messageMapper,
                           QaGoldenPairService goldenPairService,
                           ServiceKnowledgeGapMapper knowledgeGapMapper,
                           RagEvalService ragEvalService) {
    }
}
