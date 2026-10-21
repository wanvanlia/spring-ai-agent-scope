-- =============================================================================
-- 智能反诈 Agent 系统 - MySQL 业务库 DDL
-- Engine: MySQL 8.0+  Charset: utf8mb4  Collation: utf8mb4_0900_ai_ci
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `antifraud_agent`
    DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `antifraud_agent`;

-- -----------------------------------------------------------------------------
-- 1. 用户表 (审核员 / 普通用户)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username`     VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password`     VARCHAR(128) NOT NULL COMMENT '加密密码(BCrypt)',
    `real_name`    VARCHAR(64)  NULL COMMENT '真实姓名(脱敏存储)',
    `role`         VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER-普通用户, AUDITOR-审核员, ADMIN-管理员',
    `phone`        VARCHAR(32)  NULL COMMENT '联系电话(脱敏)',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- -----------------------------------------------------------------------------
-- 2. 号码信息表 (业务场景: 号码风险基础信息)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_phone_number`;
CREATE TABLE `t_phone_number` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `phone`             VARCHAR(32)  NOT NULL COMMENT '手机号(加密存储)',
    `phone_masked`      VARCHAR(32)  NULL COMMENT '手机号掩码(138****1234)',
    `carrier`           VARCHAR(32)  NULL COMMENT '运营商: CMCC-移动, CUCC-联通, CTCC-电信',
    `region`            VARCHAR(64)  NULL COMMENT '归属地',
    `call_count_24h`    INT          NOT NULL DEFAULT 0 COMMENT '近24小时呼出次数',
    `avg_duration_sec`  INT          NULL COMMENT '平均通话时长(秒)',
    `marked_count`      INT          NOT NULL DEFAULT 0 COMMENT '被标记诈骗次数',
    `blacklist_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否黑名单: 0-否, 1-是',
    `risk_level`        VARCHAR(16)  NULL COMMENT '风险等级: LOW/MEDIUM/HIGH',
    `status`            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-正常, SHUTDOWN-已关停',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_risk_level` (`risk_level`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='号码信息表';

-- -----------------------------------------------------------------------------
-- 3. Agent 会话表 (多轮对话状态管理)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_agent_session`;
CREATE TABLE `t_agent_session` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`     VARCHAR(64)  NOT NULL COMMENT '会话ID(UUID)',
    `user_id`        BIGINT       NULL COMMENT '用户ID',
    `conversation_id` VARCHAR(64) NULL COMMENT '对话ID(关联Redis记忆)',
    `intent`         VARCHAR(32)  NULL COMMENT '识别意图: QUERY/REPORT/SHUTDOWN',
    `current_state`  VARCHAR(32)  NOT NULL DEFAULT 'INIT' COMMENT '状态机当前状态',
    `context_json`   JSON         NULL COMMENT '会话上下文(缺失信息槽/中间结果)',
    `trace_id`       VARCHAR(64)  NULL COMMENT '全链路追踪ID',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '会话状态: 0-已结束, 1-进行中, 2-已中断',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_state` (`current_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话表';

-- -----------------------------------------------------------------------------
-- 4. 风险案例表 (分析研判结果与证据链)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_risk_case`;
CREATE TABLE `t_risk_case` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `case_no`         VARCHAR(64)  NOT NULL COMMENT '案件编号',
    `session_id`      VARCHAR(64)  NULL COMMENT '关联会话ID',
    `phone`           VARCHAR(32)  NOT NULL COMMENT '涉案号码',
    `phone_masked`    VARCHAR(32)  NULL COMMENT '涉案号码掩码',
    -- 防御性默认值: 避免应用层漏传时 NOT NULL 列插入失败(Field 'risk_level' doesn't have a default value)
    `risk_level`      VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级: LOW/MEDIUM/HIGH',
    `confidence`      DECIMAL(5,2) NULL COMMENT '置信度(0.00-1.00)',
    `fraud_type`      VARCHAR(64)  NULL COMMENT '诈骗类型(冒充公检法/杀猪盘等)',
    `similarity_score` DECIMAL(5,2) NULL COMMENT 'RAG匹配相似度',
    `evidence_json`   JSON         NULL COMMENT '证据链(通话频次/关键词/匹配案例)',
    `analysis_report`  JSON        NULL COMMENT 'LLM结构化风险评估报告',
    `reason`          TEXT         NULL COMMENT '研判理由',
    `handle_result`   VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '处理结果: PENDING/APPROVED/REJECTED/SHUTDOWN',
    `operator_id`     BIGINT       NULL COMMENT '操作人ID(人工审核)',
    `audit_status`    VARCHAR(32)  NULL COMMENT '审核状态: AUTO-自动, HUMAN_REVIEW-人审, CONFIRMED-已确认',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_case_no` (`case_no`),
    KEY `idx_phone` (`phone`),
    KEY `idx_risk_level` (`risk_level`),
    KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险案例表';

-- -----------------------------------------------------------------------------
-- 5. 关停工单表 (执行层落地)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_shutdown_order`;
CREATE TABLE `t_shutdown_order` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`       VARCHAR(64)  NOT NULL COMMENT '工单编号',
    `case_id`        BIGINT       NULL COMMENT '关联风险案例ID',
    `phone`          VARCHAR(32)  NOT NULL COMMENT '关停号码',
    `phone_masked`   VARCHAR(32)  NULL COMMENT '关停号码掩码',
    `reason`         VARCHAR(255) NULL COMMENT '关停理由',
    `carrier_api_resp` JSON       NULL COMMENT '运营商接口返回',
    `notify_status`  VARCHAR(16)  NULL COMMENT '受害者通知状态: SUCCESS/FAIL/SKIP',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '工单状态: PENDING/SUBMITTED/SUCCESS/FAILED/CANCELLED',
    `operator_id`    BIGINT       NULL COMMENT '操作人ID',
    `is_auto`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否自动: 0-人工, 1-自动',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_case_id` (`case_id`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关停工单表';

-- -----------------------------------------------------------------------------
-- 6. 工具调用日志表 (Agent Function Calling 审计)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_tool_call_log`;
CREATE TABLE `t_tool_call_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`    VARCHAR(64)  NULL COMMENT '关联会话ID',
    `trace_id`      VARCHAR(64)  NULL COMMENT '追踪ID',
    `agent_name`    VARCHAR(64)  NULL COMMENT '调用Agent名称(ROUTER/ANALYSIS/ACTION)',
    `tool_name`     VARCHAR(64)  NOT NULL COMMENT '工具名称',
    `arguments`     JSON         NULL COMMENT '调用参数',
    `result`        JSON         NULL COMMENT '返回值',
    `success`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功: 0-失败, 1-成功',
    `error_msg`     TEXT         NULL COMMENT '错误信息',
    `duration_ms`   INT          NULL COMMENT '耗时(毫秒)',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_tool_name` (`tool_name`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用日志表';

-- -----------------------------------------------------------------------------
-- 7. 提示词模板表 (Prompt 外部化, 支持动态调整)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS `t_prompt_template`;
CREATE TABLE `t_prompt_template` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_key` VARCHAR(64)  NOT NULL COMMENT '模板键(如 analysis_system)',
    `template_content` TEXT     NOT NULL COMMENT '模板内容(支持 {placeholder})',
    `description`  VARCHAR(255) NULL COMMENT '描述',
    `version`      INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用: 0-否, 1-是',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_key` (`template_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提示词模板表';

-- =============================================================================
-- 初始化数据
-- =============================================================================
-- 注: 以下为 BCrypt(cost=10) 哈希, 明文: admin -> Admin@123, auditor01 -> Auditor@123
--     原种子中的占位哈希长度非法(61 位, 合法应为 60), 登录必然失败, 已替换为真实可用哈希。
INSERT INTO `t_user` (`username`, `password`, `real_name`, `role`, `phone`, `status`)
VALUES
('admin', '$2a$10$hOj.8zls/TJoIUaemP5H9OC/DtiP7HiDJnu6rbpCqkARixJmXSvNe', '系统管理员', 'ADMIN', '138****0001', 1),
('auditor01', '$2a$10$0FUGoxNxFH2az92zbElpceC2VsC33jSImkcKgKgH8zBztMSY.FTk.', '审核员甲', 'AUDITOR', '138****0002', 1);

INSERT INTO `t_prompt_template` (`template_key`, `template_content`, `description`, `version`, `enabled`) VALUES
('analysis_system',
'你是一名资深反诈专家。请参考以下已知诈骗案例知识:\n{context}\n\n请对当前通话记录/号码信息进行风险研判。', '分析Agent系统提示词', 1, 1),
('router_system',
'你是反诈系统的路由智能体。请识别用户意图并从输入中提取号码字段。仅输出JSON。',
'路由Agent系统提示词', 1, 1);
