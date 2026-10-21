package com.antifraud.agent.service.tool;

import com.antifraud.agent.entity.PhoneNumber;
import com.antifraud.agent.mapper.PhoneNumberMapper;
import com.antifraud.agent.service.log.ToolCallLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 号码查询工具集(Analysis Agent 的取证工具)。
 *
 * <p>由 AgentScope {@code Toolkit} 反射注册, 模型在 ReAct 循环中<b>自主决定</b>调用哪些工具,
 * 不再由业务代码按固定顺序编排。
 * <ul>
 *   <li>validate_phone_number: 校验手机号格式</li>
 *   <li>get_call_stats: 近24小时呼出频次/平均时长</li>
 *   <li>query_call_history: 通话画像摘要</li>
 *   <li>check_blacklist: 工信部/第三方黑名单</li>
 * </ul>
 * 方法末尾的 {@link RuntimeContext} 参数不带 {@code @ToolParam}, 由框架自动注入(不会出现在工具 schema 中)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhoneQueryTool {

    private static final String AGENT_NAME = "ANALYSIS";

    private final PhoneNumberMapper phoneNumberMapper;
    private final ToolCallLogService toolCallLogService;

    @Tool(name = "validate_phone_number",
            description = "校验手机号格式是否合法(11位数字, 1开头)。返回 {valid, phone}。",
            readOnly = true, concurrencySafe = true)
    public Map<String, Object> validatePhoneNumber(
            @ToolParam(name = "phone", description = "待校验手机号") String phone,
            RuntimeContext ctx) {
        ToolRuntime.bind(ctx, AGENT_NAME);
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        boolean valid = phone != null && phone.matches("^1[3-9]\\d{9}$");
        result.put("valid", valid);
        result.put("phone", phone);
        toolCallLogService.record("validate_phone_number",
                Map.of("phone", phone), result, true, null, System.currentTimeMillis() - start);
        ToolRuntime.evidence(ctx, "validatePhone", result);
        return result;
    }

    @Tool(name = "get_call_stats",
            description = "获取号码近24小时呼出频次与平均通话时长(秒)。若库中无记录则返回模拟画像。返回 {callCount24h, avgDurationSec, markedCount}。",
            readOnly = true, concurrencySafe = true)
    public Map<String, Object> getCallStats(
            @ToolParam(name = "phone", description = "手机号") String phone,
            RuntimeContext ctx) {
        ToolRuntime.bind(ctx, AGENT_NAME);
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        PhoneNumber pn = phoneNumberMapper.selectOne(
                new LambdaQueryWrapper<PhoneNumber>().eq(PhoneNumber::getPhone, phone));
        if (pn != null) {
            result.put("callCount24h", pn.getCallCount24h());
            result.put("avgDurationSec", pn.getAvgDurationSec());
            result.put("markedCount", pn.getMarkedCount());
            result.put("blacklistFlag", pn.getBlacklistFlag());
            result.put("region", pn.getRegion());
        } else {
            // 模拟画像(演示用): 高频短时呼出特征
            result.put("callCount24h", ThreadLocalRandom.current().nextInt(40, 80));
            result.put("avgDurationSec", ThreadLocalRandom.current().nextInt(10, 30));
            result.put("markedCount", ThreadLocalRandom.current().nextInt(3, 15));
            result.put("blacklistFlag", 0);
            result.put("region", "模拟归属地");
            result.put("simulated", true);
        }
        toolCallLogService.record("get_call_stats",
                Map.of("phone", phone), result, true, null, System.currentTimeMillis() - start);
        ToolRuntime.evidence(ctx, "callStats", result);
        return result;
    }

    @Tool(name = "query_call_history",
            description = "查询号码近期通话频次、时长、被标记次数。返回完整号码画像。",
            readOnly = true, concurrencySafe = true)
    public Map<String, Object> queryCallHistory(
            @ToolParam(name = "phone", description = "手机号") String phone,
            RuntimeContext ctx) {
        ToolRuntime.bind(ctx, AGENT_NAME);
        Map<String, Object> result = getCallStats(phone, ctx);
        ToolRuntime.evidence(ctx, "callHistory", result);
        return result;
    }

    @Tool(name = "check_blacklist",
            description = "查询号码是否在工信部或第三方黑名单中。返回 {blacklist, source}。",
            readOnly = true, concurrencySafe = true)
    public Map<String, Object> checkBlacklist(
            @ToolParam(name = "phone", description = "手机号") String phone,
            RuntimeContext ctx) {
        ToolRuntime.bind(ctx, AGENT_NAME);
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        PhoneNumber pn = phoneNumberMapper.selectOne(
                new LambdaQueryWrapper<PhoneNumber>().eq(PhoneNumber::getPhone, phone));
        boolean blacklist = pn != null && pn.getBlacklistFlag() != null && pn.getBlacklistFlag() == 1;
        result.put("blacklist", blacklist);
        result.put("source", blacklist ? "工信部黑名单" : "未命中黑名单");
        toolCallLogService.record("check_blacklist",
                Map.of("phone", phone), result, true, null, System.currentTimeMillis() - start);
        ToolRuntime.evidence(ctx, "blacklist", result);
        return result;
    }
}
