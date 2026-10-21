智能反诈Agent系统架构与设计文档

> **修订说明（2026-10 更新）**：本项目智能体运行时已由「Spring AI `ChatClient` + 手写编排」迁移到 **AgentScope Java 2.x**（`io.agentscope:agentscope-core:2.0.3`）。
> Spring AI 保留 **Embedding + Milvus 向量检索**能力，以工具形式供智能体调用。本文所有章节均已按当前代码修订，
> 与实现不一致的历史设计（pgvector、LangGraph4j、Gateway/WebSocket、RabbitMQ 等）已标注为「规划/未落地」。

---

## 1. 项目概述

### 1.1 背景

随着电信网络诈骗手段的不断升级，传统基于规则的风控系统难以应对复杂多变的诈骗场景。本项目构建一个基于 **Spring Boot 3.4 + AgentScope Java 2.x + Spring AI（向量/RAG）** 的企业级智能反诈 Agent 系统。
系统利用大语言模型（LLM）的推理与工具调用能力，结合 RAG（检索增强生成）整合反诈知识库，并通过 **多智能体协作（Router / Analysis / Action / Chat）** 实现从「号码检出」到「分析研判」再到「关停处置」的全流程自动化与智能化，同时保留人工审核（人机协同）环节。

### 1.2 核心目标

1. **模型统一接入**：由 AgentScope 的 `Model` 抽象统一接入 OpenAI 兼容协议端点（阿里云百炼 `compatible-mode`），换模型只改配置。
2. **RAG 知识增强**：对接 Milvus 向量库，存储最新诈骗案例、话术特征及法规，提升模型判断准确性。
3. **智能体编排**：使用 AgentScope `ReActAgent` + `Toolkit`，让模型**自主**决定调用哪些工具（号码画像 / 黑名单 / 知识库 / 关停 / 通知）。
4. **多轮对话与状态管理**：AgentScope `AgentStateStore` 按 `(userId, sessionId)` 持久化会话上下文，业务侧以 MySQL 会话表 + 状态机管理流程。
5. **业务闭环落地**：电话诈骗场景下实现号码风险评分、证据链生成、自动/半自动关停工单与人工审核。

### 1.3 已落地能力清单（当前代码）

| 能力 | 落地情况 |
| :--- | :--- |
| RESTful 业务接口（`context-path=/antifraud`） | ✅ 对话 / 号码研判 / 案例审核 / 数据查询 / 用户管理 / 知识库摄入 |
| 前端工作台 | ✅ 原生 HTML + JS 单页应用，6 个菜单，JWT 登录 + 角色权限 |
| 4 个 AgentScope 智能体 | ✅ router / analysis / action / chat |
| 工具（Function Calling） | ✅ 7 个 `@Tool` 工具，含证据链与审计日志回填 |
| RAG | ✅ Milvus 集合 `v_knowledge_base` + 混合检索（向量 + 关键词加权） |
| 人机协同 | ✅ 中/高风险生成 PENDING 工单，人工 approve / reject |
| 安全护栏 | ✅ Prompt 注入拦截、PII 掩码、Spring Security + JWT、角色鉴权 |
| 可观测性 | ✅ TraceId 全链路、`t_tool_call_log` 工具调用审计、Actuator + Prometheus |
| 熔断降级 | ⚠️ Resilience4j 配置就绪（`llmCall`），尚未在调用链上注解接线 |
| 限流 | ⚠️ 配置项就绪（30 次/分钟/用户），尚未接线 |
| 异步批量筛查 | ❌ 未落地（规划中，见第 6 节） |

---

## 2. 总体架构设计

### 2.1 架构分层（按当前实现）

| 层级 | 组件/技术 | 职责描述 |
| :--- | :--- | :--- |
| 接入层 | Spring MVC REST、Spring Security + JWT、静态工作台 | 暴露 `/antifraud/api/**` 接口；统一 `Result{code,message,data,traceId}`；角色鉴权；前端 6 菜单工作台 |
| 应用层 | Spring Boot 3.4、MyBatis-Plus、`AntiFraudOrchestratorService` | 会话与状态机、安全护栏、业务落库、响应组装（**不含 LLM 调用细节**） |
| 智能体层 | **AgentScope Java 2.x**（`ReActAgent` × 4 + `Toolkit` + `AgentStateStore`） | 推理-行动（ReAct）循环、工具编排、结构化输出、会话记忆持久化 |
| 知识层 | Milvus（Spring AI `VectorStore`）、Embedding 模型、提示词模板 | 向量库存反诈知识；`prompts/*.st` + `t_prompt_template` 管理提示词 |
| 数据层 | MySQL 8（7 张业务表）、Redis（缓存/限流）、Milvus | 业务数据持久化；Redis 当前用于缓存与会话状态扩展位 |
| 基础设施层 | Docker Compose（MySQL/Redis/Milvus）、Actuator + Prometheus、logback | 依赖一键拉起；指标暴露；日志按天归档 |

### 2.2 技术栈选型（实际）

*   开发语言：**Java 21 (LTS)**
*   核心框架：**Spring Boot 3.4.x**、MyBatis-Plus 3.5.12
*   智能体框架：**AgentScope Java 2.0.3**（`agentscope-core` + `agentscope-extensions-model-openai`）
*   AI 接入：OpenAI 兼容协议 → 阿里云百炼 DashScope `compatible-mode`（对话 `qwen3.7-flash`；Embedding `qwen3.7-text-embedding-flash`）
*   Spring AI 1.1.3：**仅保留** `spring-ai-starter-model-openai`（Embedding）与 `spring-ai-starter-vector-store-milvus`
*   向量数据库：**Milvus**（集合 `v_knowledge_base`，1536 维，COSINE，IVF_FLAT）
*   关系型数据库：MySQL 8.0
*   缓存：Redis 7.x
*   熔断：Resilience4j（配置就绪）
*   监控：Micrometer + Prometheus（`/actuator/prometheus`）

### 2.3 架构演进：引入 AgentScope Java 2.x

**改造前**：智能体的推理-工具循环、工具按固定顺序调用、对话记忆均写在业务代码中（`AiServiceImpl` + 各 Agent 类 + Tool 类），新增能力要改三处；多轮记忆依赖自研 Redis `ChatMemoryRepository`，与框架能力割裂。

**改造后**：智能体运行时统一交给 AgentScope，业务层只做编排与落库。

```
Controller
   └── AntiFraudOrchestratorService        状态机 / 安全护栏 / 业务落库
          ├── RouterAgent    ─┐
          ├── AnalysisAgent  ─┼─► AgentScope ReActAgent（推理-工具循环 + 结构化输出）
          ├── ActionAgent    ─┤
          └── ChatAgent      ─┘
                 │  Toolkit（@Tool：号码画像 / 黑名单 / 知识库 / 关停 / 通知）
                 │  AgentStateStore（会话上下文持久化）
                 └── Model（OpenAI 兼容协议）
```

| 维度 | 改造前（Spring AI 手写） | 改造后（AgentScope 2.x） |
| :--- | :--- | :--- |
| 推理循环 | 手写固定顺序调用 | `ReActAgent` 自主决定，`maxIters=10` 防死循环 |
| 工具定义 | 手写 Bean + JSON 描述 | `@Tool` / `@ToolParam` 注解，`Toolkit.registerTool` |
| 结构化输出 | `BeanOutputConverter` | `agent.call(msgs, Xxx.class, ctx)`，不支持 `json_schema` 时自动降级为合成工具 |
| 会话记忆 | 自研 Redis ChatMemory | `AgentStateStore`（`JsonFileAgentStateStore`，可换 Redis/MySQL） |
| 新增智能体 | 改 3 处业务代码 | 加 1 个 `@Bean` 装配（模型 + 工具 + 提示词 + 状态存储） |

> 已删除的历史类：`AiService` / `AiServiceImpl` / `AiConfig` / `RedisChatMemoryRepository` / `PlaceholderTemplateRenderer`。

---

## 3. 核心模块详细设计

### 3.1 模型接入与结构化输出（AgentScope）

**模型装配**（`config/AgentScopeConfig`）：

```java
OpenAIChatModel.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)               // 注意：必须包含 /v1
        .modelName(modelName)
        .stream(false)
        .formatter(new OpenAIChatFormatter())
        .generateOptions(GenerateOptions.builder().temperature(0.1).build())
        .build();
```

*   **低温度（0.1）**：保证风控判断的严谨性与可复现性。
*   **base-url 差异（易踩坑）**：Spring AI 会自动补 `/v1`（配置为 `.../compatible-mode`），而 **AgentScope 不会**（必须配置为 `.../compatible-mode/v1`），配错即 404。
*   **启动自检**：启动时打印两端 `baseUrl` / 模型名 / Key 指纹（不泄露完整密钥），401/403/404 一类配置型故障可秒级定位。
*   **配置外置**：`antifraud.agentscope.*`，支持环境变量 `OPENAI_API_KEY` / `OPENAI_BASE_URL` / `CHAT_MODEL` 覆盖。

**结构化输出**：

```java
Msg reply = agent.call(List.of(UserMessage.of(text)), RiskAssessmentReport.class, ctx).block();
RiskAssessmentReport report = reply.getStructuredData(RiskAssessmentReport.class);
```

*   各智能体的结构化目标：`RouterResult`（意图 + 号码槽位）、`RiskAssessmentReport`（风险等级/置信度/类型/理由）、`ActionDecision`（处置结论）。
*   **降级**：兼容端点不支持 `response_format=json_schema` 时，框架自动降级为「注入 `generate_response` 合成工具」路径，**调用方代码不变**；若最终仍取不到结构化结果，业务侧兜底为「中风险 + 转人工」。

**提示词工程**：模板文件 `resources/prompts/{router,analysis,action,chat}-system.st`，由 `PromptTemplateService` 加载并预热缓存；`t_prompt_template` 表保留 DB 版本化管理能力（支持后续按版本灰度）。

### 3.2 RAG 检索增强生成系统

**存储**：Milvus 集合 `v_knowledge_base`（`initialize-schema=true` 自动建集合，字段 `id/content/metadata/embedding`，1536 维，COSINE）。

**摄入（Ingestion）**：

*   接口：`POST /api/ingest`（单条）、`POST /api/ingest/batch`（批量，按行拆分，共享来源与标签）。
*   前端「知识库」菜单支持文本直填，`source` / `tag` 写入 metadata 便于后续筛选与溯源。

**检索（Retrieval）** `RagRetrievalService`：

1.  向量语义检索：`topK=5`、`similarityThreshold=0.75`（`antifraud.rag.*`）。
2.  关键词加权：命中高危术语（安全账户、验证码、屏幕共享、公安局、转账、公检法、洗钱、保证金）每条 `+0.05`，形成 `boostedScore`。
3.  融合排序取 Top-K 后拼成上下文（`retrieveAsContext`）。
4.  **重排序**：当前用分数融合兜底，后续可引入 Cross-Encoder 提升精度（规划项）。

**生成增强**：知识库不是硬编码进 Prompt，而是以工具 `search_knowledge_base` 暴露给 Analysis Agent，由模型在 ReAct 循环中**按需**检索 —— 既省 Token，也让引用可追溯（命中片段进证据链）。

### 3.3 Agent 智能体编排与多轮对话

#### 3.3.1 智能体角色与装配（`config/AgentScopeAgentConfig`）

| 智能体 | Bean | 工具 | 结构化输出 | 职责 |
| :--- | :--- | :--- | :--- | :--- |
| Router | `routerReActAgent` | 无（低延迟） | `RouterResult` | 意图识别（QUERY/REPORT/SHUTDOWN/UNKNOWN）+ 号码槽位提取 |
| Analysis | `analysisReActAgent` | 号码画像、黑名单、知识库 | `RiskAssessmentReport` | **真正的 ReAct**：自主取证后输出风险等级、置信度、诈骗类型、理由 |
| Action | `actionReActAgent` | 关停、通知 | `ActionDecision` | 调用关停/通知工具；**工单落库由业务层单点写入**（避免双写与单号冲突） |
| Chat | `chatReActAgent` | 无 | 文本 | UNKNOWN 意图兜底问答，多轮记忆由 AgentState 承载 |

> Agent 实例无状态，单实例即可并发服务多个 `(userId, sessionId)`，会话隔离由每次调用传入的 `RuntimeContext` 决定。

#### 3.3.2 工具清单（`service/tool/*`）

| 工具名 | 归属智能体 | 说明 | 只读 |
| :--- | :--- | :--- | :--- |
| `validate_phone_number` | Analysis | 校验 11 位手机号格式 | ✅ |
| `get_call_stats` | Analysis | 近 24h 呼出频次、平均通话时长、被标记次数 | ✅ |
| `query_call_history` | Analysis | 查询完整号码画像（频次/时长/标记/黑名单） | ✅ |
| `check_blacklist` | Analysis | 工信部 / 第三方黑名单查询 | ✅ |
| `search_knowledge_base` | Analysis | **RAG** 检索相似诈骗案例与话术 | ✅ |
| `submit_shutdown_request` | Action | 向运营商提交关停工单 | ❌ |
| `notify_user` | Action | 向潜在受害者发送预警短信 | ❌ |

工具方法末尾的 `RuntimeContext ctx` 参数**不加** `@ToolParam`，由框架自动注入（不进入工具 schema），用于：

*   回填 `AgentContextHolder`（sessionId / traceId / agentName），保证 `t_tool_call_log` 审计链路在 Reactor 线程下不丢；
*   通过 `EvidenceBundle` 收集每次工具结果，形成写入 `t_risk_case.evidence_json` 的**证据链**。

#### 3.3.3 ReAct 循环与防失控

*   `maxIters = 10`：限制主循环迭代次数，防止工具调用死循环与成本失控。
*   只读工具（`@Tool(readOnly = true, concurrencySafe = true)`）可在一次推理中安全并发执行。
*   结构化结果缺失或解析失败 → 业务层兜底为**中风险 + 转人工**，绝不静默放行。

#### 3.3.4 会话记忆与状态机

*   **会话记忆**：`JsonFileAgentStateStore`（路径 `.agentscope/state`），按 `(userId, sessionId)` 分目录持久化 `AgentState`，每次 `call` 结束回写；多副本部署可替换为 Redis / MySQL 实现（接口 `AgentStateStore` 不变）。
*   **会话复用**：`chat()` 会复用该用户最近一次未结束会话（`findLatestSession`），使 AgentScope 的历史上下文跨轮次连续，支持「那 13800002222 呢？」这类追问。
*   **状态机**：`SessionState` 枚举 `INIT → INTENT_RECOGNITION → DATA_COLLECTION → ANALYSIS → HUMAN_REVIEW → EXECUTION → END`，由 `AntiFraudOrchestratorService` 驱动，当前状态与上下文写入 `t_agent_session.current_state` / `context_json`。
*   状态流转规则：
    *   缺失号码 → 回 `DATA_COLLECTION` 追问；
    *   中/高风险 → 进 `HUMAN_REVIEW`，工单置 PENDING，等待人工指令；
    *   低风险 → 直接 `END`，处置结果 `CONTINUE_OBSERVE`。

### 3.4 电话诈骗业务流程实现（端到端）

```
用户输入「帮我核查 13800001111 是否有诈骗风险」
  1) PromptGuard.check()            Prompt 注入拦截（敏感词命中直接拒绝）
  2) TraceId 生成 + 会话定位/复用     t_agent_session，MDC 透传 traceId
  3) RouterAgent.route()            结构化输出 {intent, phone, missingInfo, followUp}
        └─ 缺号码 → 返回追问话术（needFollowUp=true），流程暂停
  4) AnalysisAgent.analyze()        ReAct：自主调用
        ├─ validate_phone_number / query_call_history / get_call_stats
        ├─ check_blacklist
        └─ search_knowledge_base（RAG：如命中「冒充公检法」，相似度 0.92）
     → 输出 RiskAssessmentReport，工具结果汇总为 EvidenceBundle
  5) 落库 t_risk_case               等级/置信度/类型/理由/evidence_json/analysis_report
  6) ActionAgent.executeShutdown()
        ├─ HIGH 或 MEDIUM → 调 submit_shutdown_request + notify_user，生成 t_shutdown_order
        │                    默认 auto-shutdown-high-risk=false → 工单 PENDING 待人工
        └─ LOW            → CONTINUE_OBSERVE，不关停
  7) 人工审核（审计台）
        ├─ approve → 确认关停，工单置 SUBMITTED/SUCCESS
        └─ reject  → 驳回并填写原因，案例 handleResult=REJECTED
  8) 响应组装 → 前端展示研判卡片（风险徽标 / 证据摘要 / 命中关键词 / 工单号）
```

### 3.5 安全护栏与人机协同

*   **高风险不自动放行**：`antifraud.agent.auto-shutdown-high-risk=false`（生产默认关闭），高风险必须人工确认。
*   **工单单点写入**：工具只负责「调运营商接口」，工单落库由 `ActionAgent` 业务层统一完成，避免重复单号。
*   **审计可追溯**：每次工具调用（参数/结果/耗时/成功与否/agent 名）写入 `t_tool_call_log`，与 `traceId` 关联。

---

## 4. 数据库设计概要

### 4.1 关系型数据（MySQL，`sql/mysql_schema.sql`，共 7 张表）

| 表 | 用途 | 关键字段 |
| :--- | :--- | :--- |
| `t_user` | 账号与角色 | `username` / `password`(BCrypt) / `role`(USER/AUDITOR/ADMIN) / `status` |
| `t_phone_number` | 号码库（号码画像数据源） | `phone`(加密) / `phone_masked` / `call_count_24h` / `avg_duration_sec` / `marked_count` / `blacklist_flag` / `risk_level` / `status` |
| `t_agent_session` | 会话与状态机 | `session_id` / `user_id` / `intent` / `current_state` / `context_json` / `trace_id` |
| `t_risk_case` | 风险案例 | `case_no` / `risk_level` / `confidence` / `fraud_type` / `similarity_score` / `evidence_json` / `analysis_report` / `handle_result` / `audit_status` |
| `t_shutdown_order` | 关停工单 | `order_no` / `case_id` / `status`(PENDING/SUBMITTED/SUCCESS/FAILED/CANCELLED) / `notify_status` / `is_auto` |
| `t_tool_call_log` | 工具调用审计 | `tool_name` / `arguments` / `result` / `success` / `duration_ms` / `trace_id` |
| `t_prompt_template` | 提示词版本管理 | `template_key` / `template_content` / `version` / `enabled` |

### 4.2 向量数据（Milvus）

| 集合 | 字段 | 说明 |
| :--- | :--- | :--- |
| `v_knowledge_base` | `id` / `content` / `metadata`(JSON: source、tag) / `embedding`(1536 维) | 反诈知识、案例、话术切片；COSINE + IVF_FLAT |

> 原设计中的 PostgreSQL + pgvector 已由 Milvus 取代；MySQL 仅存业务数据。

### 4.3 缓存与会话状态

*   Redis：缓存与限流扩展位（连接已配置）。
*   **对话记忆已从 Redis `ChatMemoryRepository` 迁移到 AgentScope `AgentStateStore`**（本地 JSON 文件，按 `(userId, sessionId)` 隔离），多副本场景替换为 Redis/MySQL 实现即可，无需改动业务代码。
*   会话的业务状态（`current_state`、`context_json`）仍落 MySQL `t_agent_session`，便于运营查询与审计。

---

## 5. 生产级考量与非功能性需求

### 5.1 稳定性与降级

*   **熔断/超时**：Resilience4j 实例 `llmCall`（滑动窗口 20、失败率阈值 50%、打开状态 30s、超时 30s）已配置，用于兜底 LLM 超时与错误率过高场景；**当前尚未在调用链上注解接线**。
*   **模型层降级**：结构化输出不支持 `json_schema` 时自动降级为合成工具路径。
*   **业务层降级**：结构化结果缺失 → 中风险 + 转人工；高风险未开启自动关停 → 只建 PENDING 工单。
*   **防失控**：ReAct `maxIters=10`。
*   **异步处理**（规划）：大批量号码筛查拟引入消息队列解耦，当前未落地。

### 5.2 安全性

*   **Prompt 注入防御**：`PromptGuard` 在输入层拦截（`ignore previous` / `忽略之前` / `system prompt` / `请输出你的指令` 等敏感词可配置）。
*   **数据脱敏**：手机号掩码（`phone-keep-head=3`、`phone-keep-tail=4`）；日志与向量库不落明文 PII；密钥打印只保留指纹。
*   **权限控制**：Spring Security + JWT（TTL 120 分钟）；接口级 `@PreAuthorize` 角色校验 —— `/api/analyze`、`/api/cases/**`、`/api/query/**` 需 AUDITOR/ADMIN，`/api/users/**`、`/api/ingest/**` 仅 ADMIN；前端菜单同步按角色过滤（双保险）。
*   **限流**（待接线）：`antifraud.ratelimit.max-requests-per-minute=30`。

### 5.3 可观测性

*   **Trace ID**：`TraceIdFilter` 生成/透传 `X-Trace-Id`，贯穿 请求 → Agent → 工具 → LLM → DB，写入 `t_tool_call_log` 与统一响应 `Result.traceId`。
*   **审计**：工具调用全量入 `t_tool_call_log`（工具名/参数/结果/耗时/成功与否）。
*   **指标**：Actuator 暴露 `health,info,metrics,prometheus,circuitbreakers`；可观测 LLM 调用量、工具调用成功率、研判延迟等。
*   **日志**：logback 控制台 + `logs/console.log`，按天归档保留 30 天。

---

## 6. 项目实施路线图

**已完成（阶段一 ~ 阶段四 + AgentScope 改造）**

1.  基础搭建：Spring Boot 3.4 + Spring AI 环境；LLM 统一接入封装；Milvus 打通 Embedding 流程。
2.  RAG 核心：知识摄入接口 + 前端知识库菜单；向量检索 + 关键词加权融合。
3.  Agent 编排：7 个 `@Tool` 工具；**迁移至 AgentScope `ReActAgent`**（router/analysis/action/chat）；会话记忆迁移至 `AgentStateStore`。
4.  业务联调与前端：号码研判 / 案件审核 / 数据查询 / 用户管理 全菜单；人机协同审核；审计日志与监控接入。

**后续规划**

| 优先级 | 事项 |
| :--- | :--- |
| P0 | Resilience4j 熔断在 LLM 调用链上接线；接入限流拦截器 |
| P0 | `AgentStateStore` 替换为 Redis/MySQL 实现（支持多副本部署） |
| P1 | 引入 Cross-Encoder 重排序提升 RAG 命中质量 |
| P1 | 批量号码筛查异步化（消息队列） |
| P2 | 流式输出（SSE）改善多轮对话体验；`stream=true` 打通前后端 |
| P2 | Docker/K8s 标准化部署与 Grafana 看板；提示词在线灰度（`t_prompt_template` 版本化生效） |

---

## 7. 总结

本系统以 **AgentScope Java 2.x** 作为智能体运行时，将「推理-行动循环、工具编排、结构化输出、会话记忆」收敛到框架层，业务层只保留状态机、安全护栏与落库逻辑，新增一个智能体或工具的成本从「改三处业务代码」降为「加一个装配 Bean / 一个 `@Tool` 方法」。
在此之上，Spring AI 继续提供稳定的向量与 Embedding 能力，支撑 RAG 知识增强；Milvus + MySQL + Redis 分别承担知识检索、业务持久化与缓存；人机协同与全量审计日志保证高风险处置「可控、可追溯、可回退」。
整体既提升了诈骗号码的检出率，也通过标准化流程与人工复核降低了误封率，显著提升反诈工作的智能化水平。<br>参考资料<br>[1] [AI产品经理:Agent问答助手中多轮对话怎么设计 - 芜小匀AI产品经理](http://www.bilibili.com/video/BV1Lj4vzCENQ)<br>[2] [多智能体架构下的多轮对话上下文管理:从设计到实现 - 腾讯云](https://cloud.tencent.com/developer/article/2699038)<br>[3] [大语言模型:智能体的“大脑”革命——LLM如何驱动Agent的推理、规划与工具调用 - 腾讯云](https://cloud.tencent.com/developer/article/2595270)<br>[4] [主流智能体开发框架详细解读与对比:从单 Agent 到多智能体协作的技术选型指南 - 腾讯云](https://cloud.tencent.com/developer/article/2712008)<br>[5] [Harness Engineering 最佳实践:长运行多智能体的框架设计 - 童子龙](http://zhuanlan.zhihu.com/p/2021965663103665310)<br>[6] [这绝对是史上最强的 SpringAI 教程,开发Java Agent智能体+核心组件+代码实战,存下吧,让你少走99%的弯路! - 图灵Java面试教程](http://www.bilibili.com/video/BV1D2NE6HEcS?p=5)<br>[7] [Spring Boot 3.4 + AI Agent:打造智能化的 Java 微服务架构 - CSDN博客](https://blog.csdn.net/qq_34252622/article/details/159201020)<br>[8] [Spring AI 2.0构建企业级AI Agent:从架构设计到生产部署实战 - CSDN论坛](https://bbs.csdn.net/weixin_29596051/article/details/100261182)<br>[9] [JAVA毕业设计-融合 AI 智能问答的高校教学辅助服务系统 基于 SpringBoot 与智能 Agent 的在线教学答疑系统(源码+LW+部署文档+全bao+远程调试+代码讲解等) - CSDN博客](https://blog.csdn.net/my_qq_1808614304/article/details/163265748)<br>[10] [推荐一个基于 Spring Boot 4.0 + Java 21 + Spring AI 2.0 的大模型项目! - JavaGuide](http://zhuanlan.zhihu.com/p/2001430705301890269)<br>[11] [【2025】B站亲手带你吃透 从开箱即用到手写实现:基于 Spring AI 的 RAG 系统全流程开发!全程干货无废话!学完变大佬!还不速速看起来! - 资深bug设计工程师](http://www.bilibili.com/video/BV1XQgAz6E1c?p=15)<br>[12] [Spring AI 实战:从零构建RAG增强的智能客服系统 - 百度智能云](https://cloud.baidu.com/article/3739637)<br>[13] [【全网最新版】这可能是B站唯一把Agent智能体讲明白的教程了!手把手带你搭建自己的Agent智能体&二十+实战:工具调用+多任务识别 - 明天AI了吗](http://www.bilibili.com/video/BV1Kjtgz8Ezj?p=24)<br>[14] [【全748集】目前B站最全最细的AI Agent智能体教程,2026最新版,包含所有干货!七天就能从小白到大神!少走99%的弯路!学完即就业,带你玩转AI! - AIAgent搭建](http://www.bilibili.com/video/BV1RMgW6nENs?p=40)<br>[15] [【AI科研科普】Agent多智能体编排:前沿研究深度解读 - HorizonGazer](http://www.bilibili.com/video/BV1teNgzBEMH)<br>[16] [【全748集】目前B站最全最细AI Agent智能体全套教程,2026最新版,包含所有干货!7天带你从入门到精通,少走99%的弯路!存下吧!很难找全的! - 大模型AI应用](http://www.bilibili.com/video/BV1CvKg6DEqr?p=96)<br>[17] [【全748集】目前B站最全最细AI Agent智能体零基础教程,2026最新版,包含所有干货!手把手教你搭建专属Agent!少走99%的弯路,学完即可就业! - AIAgent搭建](http://www.bilibili.com/video/BV1Cmuq6ME6H?p=29)<br>[18] [天大教授创业,以可学习记忆为底座,他要推动AI迈向经验时代丨对话郝建业 - 新浪财经](https://baijiahao.baidu.com/s?id=1874131623246194754&wfr=spider&for=pc)<br>[19] [继开源H3之后,MiniMax以Design切入万亿级数字内容大市场 - 财经移动新媒体](https://baijiahao.baidu.com/s?id=1874373646923933991&wfr=spider&for=pc)<br>[20] [26岁离开10亿私募,一个交易天才与他的AI Trading创业|独家对话 - 新浪财经](https://baijiahao.baidu.com/s?id=1874281194292009072&wfr=spider&for=pc)<br>[21] [AI Agent的工具调用机制架构设计:从单工具调用到多工具智能编排的完整方案 - CSDN博客](https://blog.csdn.net/jiang_style/article/details/163020038)<br>[22] [Java AI 高级全能工程师实战:从RAG知识库到Agent智能体全流程开发 - 腾讯云](https://cloud.tencent.com/developer/article/2715009)<br>[23] [收藏|小白也能看懂!手把手教你设计大模型多轮对话Agent(附核心策略) - CSDN博客](https://blog.csdn.net/weixin_59191169/article/details/160114778)<br>[24] [AgentScope Java 官方文档（快速开始 / Agent / Tool / Model）](https://java.agentscope.io/)<br><br>百度AI生成，内容仅供参考
