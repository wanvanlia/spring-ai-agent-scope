package com.antifraud.agent.controller;

import com.antifraud.agent.common.Result;
import com.antifraud.agent.entity.RiskCase;
import com.antifraud.agent.entity.ShutdownOrder;
import com.antifraud.agent.model.vo.ChatResponse;
import com.antifraud.agent.model.vo.FraudAnalysisVO;
import com.antifraud.agent.service.business.AntiFraudOrchestratorService;
import com.antifraud.agent.service.business.RiskCaseAuditService;
import com.antifraud.agent.service.rag.KnowledgeIngestionService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 反诈 Agent REST 接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AntiFraudController {

    private final AntiFraudOrchestratorService orchestrator;
    private final RiskCaseAuditService auditService;
    private final KnowledgeIngestionService ingestionService;

    /** 多轮对话入口(普通用户可调用) */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest req, Authentication auth) {
        Long userId = resolveUserId(auth);
        ChatResponse resp = orchestrator.chat(userId, req.getMessage());
        return Result.success(resp);
    }

    /** 以号码直接触发研判(管理员) */
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN')")
    public Result<FraudAnalysisVO> analyze(@RequestParam @NotBlank String phone, Authentication auth) {
        Long userId = resolveUserId(auth);
        FraudAnalysisVO vo = orchestrator.analyzePhone(userId, phone);
        return Result.success(vo);
    }

    /** 知识库摄入(管理员) */
    @PostMapping("/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> ingest(@RequestBody IngestRequest req) {
        ingestionService.ingest(req.getContent(), Map.of(
                "source", req.getSource() == null ? "manual" : req.getSource(),
                "tag", req.getTag() == null ? "" : req.getTag()));
        return Result.success();
    }

    /** 批量摄入 */
    @PostMapping("/ingest/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> ingestBatch(@RequestBody List<IngestRequest> batch) {
        List<Document> docs = batch.stream()
                .map(i -> new Document(i.getContent(), Map.of(
                        "source", i.getSource() == null ? "manual" : i.getSource(),
                        "tag", i.getTag() == null ? "" : i.getTag())))
                .toList();
        ingestionService.ingest(docs);
        return Result.success();
    }

    /** 查询案例 */
    @GetMapping("/cases/{caseNo}")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN')")
    public Result<RiskCase> getCase(@PathVariable String caseNo) {
        return Result.success(auditService.findByCaseNo(caseNo));
    }

    /** 人工确认关停 */
    @PostMapping("/cases/{caseNo}/approve")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN')")
    public Result<ShutdownOrder> approve(@PathVariable String caseNo, Authentication auth) {
        Long operatorId = resolveUserId(auth);
        return Result.success(auditService.approve(caseNo, operatorId));
    }

    /** 人工驳回 */
    @PostMapping("/cases/{caseNo}/reject")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN')")
    public Result<Void> reject(@PathVariable String caseNo, @RequestParam(required = false) String reason,
                               Authentication auth) {
        Long operatorId = resolveUserId(auth);
        auditService.reject(caseNo, operatorId, reason);
        return Result.success();
    }

    private Long resolveUserId(Authentication auth) {
        // JWT 认证后, principal 为用户名; 真实场景查 t_user.id。此处返回占位 ID。
        return auth == null ? 0L : auth.getName().hashCode() & 0xFFFFFFFFL;
    }

    @Data
    public static class ChatRequest {
        private String message;
    }

    @Data
    public static class IngestRequest {
        private String content;
        private String source;
        private String tag;
    }
}
