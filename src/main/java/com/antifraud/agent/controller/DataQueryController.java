package com.antifraud.agent.controller;

import com.antifraud.agent.common.Result;
import com.antifraud.agent.entity.PhoneNumber;
import com.antifraud.agent.entity.RiskCase;
import com.antifraud.agent.entity.ShutdownOrder;
import com.antifraud.agent.mapper.PhoneNumberMapper;
import com.antifraud.agent.mapper.RiskCaseMapper;
import com.antifraud.agent.mapper.ShutdownOrderMapper;
import com.antifraud.agent.model.dto.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据查询接口: 号码库 / 风险案例 / 关停工单 的分页检索(供控制台查询界面使用)。
 * <p>涉及 PII 与处置数据, 仅审核员与管理员可访问。
 * <p>分页说明: 这里手工拼接 MySQL 的 {@code limit/offset}, 不依赖 MP 分页插件
 * (3.5.12 的 PaginationInnerInterceptor 已拆分到独立 jar, 见 MybatisPlusConfig 注释)。
 */
@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class DataQueryController {

    /** 单页最大条数, 防止前端传超大 size 拖垮数据库 */
    private static final long MAX_SIZE = 100L;

    private final PhoneNumberMapper phoneNumberMapper;
    private final RiskCaseMapper riskCaseMapper;
    private final ShutdownOrderMapper shutdownOrderMapper;

    /** 号码库 t_phone_number */
    @GetMapping("/phones")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN')")
    public Result<PageResult<PhoneNumber>> phones(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<PhoneNumber> cond = new LambdaQueryWrapper<PhoneNumber>()
                .like(StringUtils.hasText(phone), PhoneNumber::getPhone, phone)
                .eq(StringUtils.hasText(riskLevel), PhoneNumber::getRiskLevel, riskLevel)
                .eq(StringUtils.hasText(status), PhoneNumber::getStatus, status)
                .orderByDesc(PhoneNumber::getId);
        return Result.success(query(phoneNumberMapper, cond, page, size));
    }

    /** 风险案例 t_risk_case */
    @GetMapping("/risk-cases")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN')")
    public Result<PageResult<RiskCase>> riskCases(
            @RequestParam(required = false) String caseNo,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String handleResult,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<RiskCase> cond = new LambdaQueryWrapper<RiskCase>()
                .like(StringUtils.hasText(caseNo), RiskCase::getCaseNo, caseNo)
                .like(StringUtils.hasText(phone), RiskCase::getPhone, phone)
                .eq(StringUtils.hasText(riskLevel), RiskCase::getRiskLevel, riskLevel)
                .eq(StringUtils.hasText(handleResult), RiskCase::getHandleResult, handleResult)
                .orderByDesc(RiskCase::getId);
        return Result.success(query(riskCaseMapper, cond, page, size));
    }

    /** 关停工单 t_shutdown_order */
    @GetMapping("/shutdown-orders")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN')")
    public Result<PageResult<ShutdownOrder>> shutdownOrders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<ShutdownOrder> cond = new LambdaQueryWrapper<ShutdownOrder>()
                .like(StringUtils.hasText(orderNo), ShutdownOrder::getOrderNo, orderNo)
                .like(StringUtils.hasText(phone), ShutdownOrder::getPhone, phone)
                .eq(StringUtils.hasText(status), ShutdownOrder::getStatus, status)
                .orderByDesc(ShutdownOrder::getId);
        return Result.success(query(shutdownOrderMapper, cond, page, size));
    }

    /**
     * 分页查询: 使用 MyBatis-Plus 的 selectPage(依赖 MybatisPlusConfig 中注册的分页拦截器)。
     */
    private <T> PageResult<T> query(BaseMapper<T> mapper, LambdaQueryWrapper<T> cond, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_SIZE);
        Page<T> p = mapper.selectPage(new Page<>(current, pageSize), cond);
        if (p == null) {
            return PageResult.of(List.of(), 0, current, pageSize);
        }
        return PageResult.of(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
    }
}
