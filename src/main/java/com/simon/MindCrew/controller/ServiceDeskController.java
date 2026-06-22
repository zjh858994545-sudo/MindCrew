package com.simon.MindCrew.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.simon.MindCrew.common.result.Result;
import com.simon.MindCrew.entity.ServiceKnowledgeGap;
import com.simon.MindCrew.entity.ServiceTicket;
import com.simon.MindCrew.entity.ServiceTicketEvent;
import com.simon.MindCrew.service.ServiceDeskKnowledgeIndexService;
import com.simon.MindCrew.service.ServiceDeskService;
import com.simon.MindCrew.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service-desk")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class ServiceDeskController {

    private final ServiceDeskService serviceDeskService;
    private final ServiceDeskKnowledgeIndexService knowledgeIndexService;
    private final UserService userService;

    @GetMapping("/tickets")
    public Result<IPage<ServiceTicket>> tickets(@RequestParam(defaultValue = "1") int current,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) String keyword) {
        return Result.success(serviceDeskService.page(current, size, status, category, keyword));
    }

    @GetMapping("/tickets/{id}")
    public Result<ServiceTicket> ticket(@PathVariable Long id) {
        return Result.success(serviceDeskService.getById(id));
    }

    @GetMapping("/tickets/{id}/events")
    public Result<List<ServiceTicketEvent>> events(@PathVariable Long id) {
        return Result.success(serviceDeskService.events(id));
    }

    @PostMapping("/tickets")
    public Result<Long> create(@RequestBody CreateTicketRequest request) {
        Long id = serviceDeskService.create(new ServiceDeskService.CreateTicketCommand(
                request.getTitle(),
                request.getRequester(),
                request.getRequesterRole(),
                request.getDepartment(),
                request.getPriority(),
                request.getChannel(),
                request.getCategory(),
                request.getQuestion(),
                request.getExpectedOutcome(),
                request.getKbScope()
        ), actor());
        return Result.success(id);
    }

    @PostMapping("/tickets/{id}/draft")
    public Result<ServiceDeskService.DraftResult> draft(@PathVariable Long id) {
        return Result.success(serviceDeskService.generateDraft(id, currentUserId()));
    }

    @PostMapping("/tickets/{id}/accept")
    public Result<ServiceTicket> accept(@PathVariable Long id, @RequestBody AcceptRequest request) {
        return Result.success(serviceDeskService.acceptDraft(id, request.getFinalAnswer(), actor()));
    }

    @PostMapping("/tickets/{id}/golden-pair/retry")
    public Result<ServiceTicket> retryGoldenPair(@PathVariable Long id) {
        return Result.success(serviceDeskService.retryGoldenPairSync(id, actor()));
    }

    @PostMapping("/tickets/{id}/reject")
    public Result<ServiceTicket> reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        return Result.success(serviceDeskService.rejectDraft(id, request.getReason(), actor()));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(serviceDeskService.stats());
    }

    @GetMapping("/knowledge-gaps")
    public Result<List<ServiceKnowledgeGap>> knowledgeGaps() {
        return Result.success(serviceDeskService.knowledgeGaps());
    }

    @PostMapping("/knowledge/reindex")
    public Result<ServiceDeskKnowledgeIndexService.ReindexReport> reindexKnowledge() {
        return Result.success(knowledgeIndexService.reindexServiceDeskKnowledge());
    }

    private String actor() {
        try {
            Long userId = userService.getCurrentUserId();
            return userId == null ? "system" : "user-" + userId;
        } catch (Exception ex) {
            return "system";
        }
    }

    private String currentUserId() {
        try {
            Long userId = userService.getCurrentUserId();
            return userId == null ? "0" : String.valueOf(userId);
        } catch (Exception ex) {
            return "0";
        }
    }

    @Data
    public static class CreateTicketRequest {
        private String title;
        private String requester;
        private String requesterRole;
        private String department;
        private String priority;
        private String channel;
        private String category;
        private String question;
        private String expectedOutcome;
        private String kbScope;
    }

    @Data
    public static class AcceptRequest {
        private String finalAnswer;
    }

    @Data
    public static class RejectRequest {
        private String reason;
    }
}
