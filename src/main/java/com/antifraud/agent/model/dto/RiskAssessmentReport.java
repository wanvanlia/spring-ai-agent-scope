package com.antifraud.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.antifraud.agent.common.json.LenientStringDeserializer;
import lombok.Data;

import java.util.List;

/**
 * LLM 结构化风险评估报告 (BeanOutputConverter 强制 JSON 输出)
 */
@Data
public class RiskAssessmentReport {

    /** 涉案号码 */
    private String phone;

    /** 风险等级: LOW / MEDIUM / HIGH */
    private String riskLevel;

    /** 置信度 0.00-1.00 */
    private Double confidence;

    /** 诈骗类型 */
    private String fraudType;

    /**
     * 命中特征关键词。
     * <p>模型可能返回字符串数组, 也可能返回对象数组; 用 LenientStringDeserializer 兼容两种情况,
     * 并用 ACCEPT_SINGLE_VALUE_AS_ARRAY 兼容"单个值而非数组"的情况。
     */
    @JsonProperty("hitKeywords")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @JsonDeserialize(contentUsing = LenientStringDeserializer.class)
    private List<String> hitKeywords;

    /** 命中相似案例 */
    @JsonProperty("matchedCases")
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @JsonDeserialize(contentUsing = LenientStringDeserializer.class)
    private List<String> matchedCases;

    /** 研判理由 */
    private String reason;

    /** 处置建议: AUTO_SHUTDOWN / HUMAN_REVIEW / CONTINUE_OBSERVE */
    private String suggestion;
}
