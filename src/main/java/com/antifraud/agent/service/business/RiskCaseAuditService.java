package com.antifraud.agent.service.business;

import com.antifraud.agent.common.enums.RiskLevel;
import com.antifraud.agent.common.enums.SessionState;
import com.antifraud.agent.entity.AgentSession;
import com.antifraud.agent.entity.RiskCase;
import com.antifraud.agent.entity.ShutdownOrder;
import com.antifraud.agent.mapper.RiskCaseMapper;
import com.antifraud.agent.mapper.ShutdownOrderMapper;
import com.antifraud.agent.service.agent.ActionAgent;
import com.antifraud.agent.service.session.AgentSessionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 人工审核服务: 审核员确认/驳回风险案例, 触发关停执行。
 * <p>对应文档 3.3.2 HUMAN_REVIEW 状态流转。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskCaseAuditService {

    private final RiskCaseMapper riskCaseMapper;
    private final ShutdownOrderMapper shutdownOrderMapper;
    private final ActionAgent actionAgent;
    private final AgentSessionService sessionService;

    public RiskCase findByCaseNo(String caseNo) {
        return riskCaseMapper.selectOne(
                new LambdaQueryWrapper<RiskCase>().eq(RiskCase::getCaseNo, caseNo));
    }

    /**
     * 人工确认关停。
     */
    public ShutdownOrder approve(String caseNo, Long operatorId) {
        RiskCase rc = findByCaseNo(caseNo);
        if (rc == null) {
            throw new IllegalArgumentException("案例不存在: " + caseNo);
        }
        rc.setOperatorId(operatorId);
        rc.setAuditStatus("CONFIRMED");
        rc.setHandleResult("SHUTDOWN");
        rc.getPhoneMasked(); // 掩码已在研判阶段写入

        RiskLevel level = normalize(rc.getRiskLevel());
        // 直接执行关停(人工已确认)
        ShutdownOrder order = actionAgent.executeShutdown(rc, level);
        // 关联待审工单状态更新
        ShutdownOrder pending = shutdownOrderMapper.selectOne(
                new LambdaQueryWrapper<ShutdownOrder>()
                        .eq(ShutdownOrder::getCaseId, rc.getId())
                        .eq(ShutdownOrder::getStatus, "PENDING"));
        if (pending != null) {
            pending.setStatus("CANCELLED");
            shutdownOrderMapper.updateById(pending);
        }
        order.setOperatorId(operatorId);
        shutdownOrderMapper.updateById(order);
        log.info("人工确认关停 caseNo={} orderNo={} operator={}", caseNo, order.getOrderNo(), operatorId);
        return order;
    }

    /** 驳回(不关停) */
    public void reject(String caseNo, Long operatorId, String reason) {
        RiskCase rc = findByCaseNo(caseNo);
        if (rc == null) {
            throw new IllegalArgumentException("案例不存在: " + caseNo);
        }
        rc.setOperatorId(operatorId);
        rc.setAuditStatus("CONFIRMED");
        rc.setHandleResult("REJECTED");
        riskCaseMapper.updateById(rc);
        sessionService.finish(rc.getSessionId());
        log.info("驳回关停 caseNo={} operator={} reason={}", caseNo, operatorId, reason);
    }

    private RiskLevel normalize(String raw) {
        if (raw == null) {
            return RiskLevel.MEDIUM;
        }
        try {
            return RiskLevel.valueOf(raw.toUpperCase());
        } catch (Exception e) {
            return RiskLevel.MEDIUM;
        }
    }
}
