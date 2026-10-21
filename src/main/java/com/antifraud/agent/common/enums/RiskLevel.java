package com.antifraud.agent.common.enums;

/**
 * 风险等级
 */
public enum RiskLevel {
    LOW("低风险"),
    MEDIUM("中风险", true),   // 中风险需触发人机协同
    HIGH("高风险", true);    // 高风险需触发人机协同 / 自动关停

    private final String desc;
    private final boolean needHumanReview;

    RiskLevel(String desc) {
        this(desc, false);
    }

    RiskLevel(String desc, boolean needHumanReview) {
        this.desc = desc;
        this.needHumanReview = needHumanReview;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isNeedHumanReview() {
        return needHumanReview;
    }
}
