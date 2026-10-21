package com.antifraud.agent.model.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 反诈分析结果视图对象
 */
@Data
public class FraudAnalysisVO {
    private String sessionId;
    private String caseNo;
    private String phoneMasked;
    private String riskLevel;
    private Double confidence;
    private String fraudType;
    private String reason;
    private String suggestion;
    private String handleResult;
    /** 关停工单号(若已提交) */
    private String orderNo;
    /** 是否转人工审核 */
    private boolean needHumanReview;
    /** 状态机当前状态 */
    private String currentState;
    /** 证据摘要 */
    private Map<String, Object> evidence;
    /** 命中关键词 */
    private List<String> hitKeywords;
}
