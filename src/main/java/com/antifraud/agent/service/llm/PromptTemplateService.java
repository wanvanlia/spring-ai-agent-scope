package com.antifraud.agent.service.llm;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 提示词模板管理: 优先从数据库 t_prompt_template 加载(支持动态调整), 兜底 classpath:prompts/*.st。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptTemplateService {

    private static final String CLASSPATH_DIR = "prompts/";

    /** 缓存: key -> 模板内容 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 获取模板内容。生产环境可在此处查询 t_prompt_template 表(enabled=1, version 最大)并刷新缓存。
     */
    public String get(String key) {
        return cache.computeIfAbsent(key, k -> {
            // 1. 兜底: 读取 classpath 模板
            try {
                Resource resource = new ClassPathResource(CLASSPATH_DIR + k + ".st");
                if (resource.exists()) {
                    return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                log.warn("加载 classpath 提示词模板失败: {}", k, e);
            }
            return "";
        });
    }

    @PostConstruct
    public void warmUp() {
        // AgentScope 各智能体的系统提示词
        get("router-system");
        get("analysis-system");
        get("action-system");
        get("chat-system");
        log.info("提示词模板预热完成: {}", cache.keySet());
    }
}
