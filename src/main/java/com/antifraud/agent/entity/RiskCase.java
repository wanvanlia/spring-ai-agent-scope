package com.antifraud.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风险案例表 (t_risk_case)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_risk_case")
public class RiskCase extends Model<RiskCase> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String caseNo;

    private String sessionId;

    private String phone;

    private String phoneMasked;

    /** LOW/MEDIUM/HIGH */
    private String riskLevel;

    private BigDecimal confidence;

    private String fraudType;

    private BigDecimal similarityScore;

    /** 证据链(JSON) */
    private String evidenceJson;

    /** LLM 结构化风险评估报告(JSON) */
    private String analysisReport;

    private String reason;

    /** PENDING/APPROVED/REJECTED/SHUTDOWN */
    private String handleResult;

    private Long operatorId;

    /** AUTO/HUMAN_REVIEW/CONFIRMED */
    private String auditStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
