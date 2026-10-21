package com.antifraud.agent.service.tool;

import com.antifraud.agent.common.util.MaskingUtil;
import com.antifraud.agent.service.log.ToolCallLogService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 执行层工具(Action Agent): 关停/通知。
 *
 * <p>定位: 模拟"外部运营商接口", 只返回受理结果, <b>不落库</b>。
 * 工单持久化统一由 {@code ActionAgent} 业务层负责, 避免同一个工单被插入两次导致单号冲突。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionTool {

    private static final String AGENT_NAME = "ACTION";

    private final ToolCallLogService toolCallLogService;
    private final MaskingUtil maskingUtil;

    @Tool(name = "submit_shutdown_request",
            description = "向运营商接口提交号码关停工单。返回 {status: SUBMITTED|FAILED}。")
    public Map<String, Object> submitShutdownRequest(
            @ToolParam(name = "phone", description = "关停号码") String phone,
            @ToolParam(name = "reason", description = "关停理由") String reason,
            RuntimeContext ctx) {
        ToolRuntime.bind(ctx, AGENT_NAME);
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        try {
            // 模拟调用运营商 API: 仅返回受理回执, 不写库(由 ActionAgent 统一落库)
            result.put("status", "SUBMITTED");
            result.put("carrierResp", "{\"code\":0,\"msg\":\"accepted\"}");
            toolCallLogService.record("submit_shutdown_request",
                    args(phone, reason), result, true, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("提交关停工单失败, phone={}", phone, e);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            toolCallLogService.record("submit_shutdown_request",
                    args(phone, reason), result, false, e.getMessage(), System.currentTimeMillis() - start);
        }
        return result;
    }

    @Tool(name = "notify_user",
            description = "向潜在受害者发送预警短信。返回 {notifyStatus}。")
    public Map<String, Object> notifyUser(
            @ToolParam(name = "phone", description = "受害者手机号") String phone,
            RuntimeContext ctx) {
        ToolRuntime.bind(ctx, AGENT_NAME);
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        // 模拟短信网关
        result.put("notifyStatus", "SUCCESS");
        result.put("message", "已向 " + maskingUtil.maskPhone(phone) + " 发送反诈预警短信");
        toolCallLogService.record("notify_user",
                args(phone, null), result, true, null, System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 构造日志参数。
     * <p>注意: 不能用 Map.of(...) —— 它不允许 null 值, 而 reason/phone 都可能为空,
     * 会直接抛 NullPointerException(ImmutableCollections.MapN 中的 requireNonNull)。
     */
    private static Map<String, Object> args(String phone, String reason) {
        Map<String, Object> args = new HashMap<>();
        args.put("phone", phone == null ? "" : phone);
        if (reason != null) {
            args.put("reason", reason);
        }
        return args;
    }
}
