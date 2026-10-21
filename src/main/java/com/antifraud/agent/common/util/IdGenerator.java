package com.antifraud.agent.common.util;

import java.util.UUID;

/**
 * 业务单号生成器。
 * <p>
 * 不能只用 {@code System.currentTimeMillis()} 作为单号: 同一毫秒内会产生完全相同的单号,
 * 命中 t_risk_case.uk_case_no / t_shutdown_order.uk_order_no 唯一索引后
 * 会抛 Duplicate entry ... for key 'uk_order_no'。
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    /** 风险案例编号: RC + 时间戳 + 随机后缀 */
    public static String nextCaseNo() {
        return "RC" + System.currentTimeMillis() + suffix();
    }

    /** 关停工单编号: SD + 时间戳 + 随机后缀 */
    public static String nextOrderNo() {
        return "SD" + System.currentTimeMillis() + suffix();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
