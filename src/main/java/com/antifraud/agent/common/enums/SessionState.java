package com.antifraud.agent.common.enums;

/**
 * Agent 多轮对话状态机
 * <pre>
 * INIT -> INTENT_RECOGNITION -> DATA_COLLECTION -> ANALYSIS
 *      -> HUMAN_REVIEW(可选) -> EXECUTION -> END
 * </pre>
 */
public enum SessionState {
    INIT,
    INTENT_RECOGNITION,
    DATA_COLLECTION,
    ANALYSIS,
    HUMAN_REVIEW,
    EXECUTION,
    END
}
