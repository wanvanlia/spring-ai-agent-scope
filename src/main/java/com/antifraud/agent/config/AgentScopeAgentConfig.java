package com.antifraud.agent.config;

import com.antifraud.agent.service.llm.PromptTemplateService;
import com.antifraud.agent.service.tool.ActionTool;
import com.antifraud.agent.service.tool.KnowledgeTool;
import com.antifraud.agent.service.tool.PhoneQueryTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智能体装配: 把"模型 + 工具 + 提示词 + 状态存储"组装成 4 个 AgentScope {@link ReActAgent}。
 *
 * <pre>
 *   routerAgent   : 意图识别 + 号码槽位提取(结构化输出, 无工具)
 *   analysisAgent : 风险研判(号码画像/黑名单/知识库检索工具 + 结构化输出)
 *   actionAgent   : 处置执行(关停/通知工具 + 结构化输出)
 *   chatAgent     : 通用反诈问答(无工具, 依赖 AgentState 做多轮记忆)
 * </pre>
 *
 * <p>Agent 实例本身是无状态的, 单实例即可并发服务多个 (userId, sessionId),
 * 会话隔离由每次调用传入的 {@code RuntimeContext} 决定。
 */
@Configuration
public class AgentScopeAgentConfig {

    /**
     * 路由智能体: 只做意图识别与槽位提取, 不挂工具, 保证低延迟。
     * <p>注意: bean 名不能叫 {@code routerAgent} —— {@code service.agent.RouterAgent} 作为
     * {@code @Component} 的默认 bean 名就是 {@code routerAgent}, 同名会导致覆盖被拒绝
     * (isAllowBeanDefinitionOverriding=false)。
     */
    @Bean
    public ReActAgent routerReActAgent(Model model,
                                  JsonFileAgentStateStore stateStore,
                                  PromptTemplateService promptTemplateService,
                                  @Value("${antifraud.agentscope.max-iters:10}") int maxIters) {
        return ReActAgent.builder()
                .name("router")
                .sysPrompt(promptTemplateService.get("router-system"))
                .model(model)
                .stateStore(stateStore)
                .maxIters(maxIters)
                .build();
    }

    /**
     * 研判智能体: 真正的 ReAct 循环 —— 由模型自主决定调用哪些取证工具,
     * 采集完证据后再按结构化 schema 输出 {@code RiskAssessmentReport}。
     */
    @Bean
    public ReActAgent analysisReActAgent(Model model,
                                    JsonFileAgentStateStore stateStore,
                                    PromptTemplateService promptTemplateService,
                                    PhoneQueryTool phoneQueryTool,
                                    KnowledgeTool knowledgeTool,
                                    @Value("${antifraud.agentscope.max-iters:10}") int maxIters) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(phoneQueryTool);
        toolkit.registerTool(knowledgeTool);
        return ReActAgent.builder()
                .name("analysis")
                .sysPrompt(promptTemplateService.get("analysis-system"))
                .model(model)
                .toolkit(toolkit)
                .stateStore(stateStore)
                .maxIters(maxIters)
                .build();
    }

    /**
     * 处置智能体: 关停/通知工具由模型在 ReAct 循环内调用,
     * 工单落库仍由 {@code ActionAgent} 业务层统一负责(避免双写与单号冲突)。
     */
    @Bean
    public ReActAgent actionReActAgent(Model model,
                                  JsonFileAgentStateStore stateStore,
                                  PromptTemplateService promptTemplateService,
                                  ActionTool actionTool,
                                  @Value("${antifraud.agentscope.max-iters:10}") int maxIters) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(actionTool);
        return ReActAgent.builder()
                .name("action")
                .sysPrompt(promptTemplateService.get("action-system"))
                .model(model)
                .toolkit(toolkit)
                .stateStore(stateStore)
                .maxIters(maxIters)
                .build();
    }

    /** 通用问答智能体: 承接 UNKNOWN 意图的兜底对话, 多轮记忆由 AgentState 承载(替代原 Redis ChatMemory)。 */
    @Bean
    public ReActAgent chatReActAgent(Model model,
                                JsonFileAgentStateStore stateStore,
                                PromptTemplateService promptTemplateService,
                                @Value("${antifraud.agentscope.max-iters:10}") int maxIters) {
        return ReActAgent.builder()
                .name("chat")
                .sysPrompt(promptTemplateService.get("chat-system"))
                .model(model)
                .stateStore(stateStore)
                .maxIters(maxIters)
                .build();
    }
}
