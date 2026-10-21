package com.antifraud.agent.common.guard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 注入防御: 在输入层检测试图绕过系统限制的恶意 Prompt。
 * <p>对应文档 5.2 安全性。
 */
@Slf4j
@Component
public class PromptGuard {

    @Value("${antifraud.security.prompt-injection-keywords:}")
    private List<String> keywords;

    /** 检测到注入返回 true */
    public boolean detectInjection(String input) {
        if (input == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String lower = input.toLowerCase();
        return keywords.stream().anyMatch(k -> lower.contains(k.toLowerCase()));
    }

    /** 校验并抛出异常 */
    public void check(String input) {
        if (detectInjection(input)) {
            log.warn("检测到疑似 Prompt 注入, 已拦截: {}", input);
            throw new IllegalArgumentException("输入包含敏感指令, 已拦截。");
        }
    }
}
