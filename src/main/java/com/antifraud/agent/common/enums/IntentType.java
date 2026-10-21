package com.antifraud.agent.common.enums;

/**
 * 用户意图类型(路由 Agent 分发依据)
 */
public enum IntentType {
    /** 查询号码风险 */
    QUERY,
    /** 举报诈骗 */
    REPORT,
    /** 申请关停 */
    SHUTDOWN,
    /** 未识别 */
    UNKNOWN
}
