package com.antifraud.agent.config;

import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * AgentScope Java 2.x 基础设施配置: 模型 + 会话状态存储。
 *
 * <p><b>分层约定</b>
 * <ul>
 *   <li>模型: 统一走 OpenAI 兼容协议(阿里云百炼 compatible-mode / 其它兼容端点均可),
 *       由 {@code antifraud.agentscope.*} 配置驱动, 换模型只改配置。</li>
 *   <li>状态: {@code AgentStateStore} 负责会话上下文(AgentState)的加载与回写,
 *       按 {@code (userId, sessionId)} 隔离, 这里的单机会话存储适用于开发/单节点;
 *       多副本部署可替换为 Redis / MySQL 实现。</li>
 * </ul>
 *
 * <p><b>启动日志</b> 会打印当前实际连接的大模型 URL / 模型名 / Key 指纹, 便于排查
 * 401(鉴权) / 403(配额) / 404(路径) 一类的"配置型"故障。
 */
@Slf4j
@Configuration
public class AgentScopeConfig {

    // ---- Spring AI 侧配置(现仅用于 Embedding / 向量检索), 启动时一并打印, 便于对照两个 base-url ----
    @Value("${spring.ai.openai.base-url:}")
    private String springAiBaseUrl;

    @Value("${spring.ai.openai.chat.options.model:}")
    private String springAiChatModel;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String springAiEmbeddingModel;

    @PostConstruct
    public void logSpringAiModelConfig() {
        log.info("[ModelConfig][Spring AI] baseUrl={} chatModel={} embeddingModel={}",
                springAiBaseUrl, springAiChatModel, springAiEmbeddingModel);
    }

    /**
     * AgentScope 模型实例。
     * <p>结构化输出说明: 兼容端点若不支持 {@code response_format=json_schema},
     * 框架会自动降级为"注入 generate_response 合成工具"的路径, 调用方代码无需变化。
     */
    @Bean
    public Model antifraudChatModel(
            @Value("${antifraud.agentscope.api-key}") String apiKey,
            @Value("${antifraud.agentscope.base-url}") String baseUrl,
            @Value("${antifraud.agentscope.model}") String modelName,
            @Value("${antifraud.agentscope.temperature:0.1}") double temperature,
            @Value("${antifraud.agentscope.stream:false}") boolean stream) {
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .stream(stream)
                .formatter(new OpenAIChatFormatter())
                .generateOptions(GenerateOptions.builder()
                        .temperature(temperature)
                        .build())
                .build();
        // 启动时打印当前实际连接的大模型端点与参数(api-key 仅打印指纹, 避免密钥泄露)
        log.info("[ModelConfig][AgentScope] model={} baseUrl={} chatEndpoint={} temperature={} stream={} apiKey={}",
                modelName, baseUrl, trimTrailingSlash(baseUrl) + "/chat/completions",
                temperature, stream, fingerprint(apiKey));
        return model;
    }

    /**
     * 会话状态存储: 每次 {@code call} 结束后回写 AgentState, 下次同一 sessionId 自动恢复,
     * 因此多轮对话、工具调用上下文都具备连续性。
     */
    @Bean
    public JsonFileAgentStateStore agentStateStore(
            @Value("${antifraud.agentscope.state-store-path:.agentscope/state}") String path) {
        log.info("[ModelConfig][AgentScope] stateStorePath={}", Path.of(path).toAbsolutePath());
        return new JsonFileAgentStateStore(Path.of(path));
    }

    /** 去掉末尾斜杠, 避免拼出 {@code //chat/completions}。 */
    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Key 指纹: 只保留前缀与长度, 便于确认"用的是哪个 Key", 不泄露完整密钥。 */
    private static String fingerprint(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "<empty>";
        }
        String prefix = apiKey.length() <= 8 ? "" : apiKey.substring(0, 8);
        return prefix + "***(len=" + apiKey.length() + ")";
    }
}
