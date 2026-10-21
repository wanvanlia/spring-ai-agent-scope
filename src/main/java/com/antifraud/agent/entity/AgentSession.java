package com.antifraud.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Agent 会话表 (t_agent_session)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_session")
public class AgentSession extends Model<AgentSession> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String conversationId;

    /** 意图: QUERY/REPORT/SHUTDOWN */
    private String intent;

    /** 状态机当前状态 */
    private String currentState;

    /** 会话上下文(JSON) */
    private String contextJson;

    private String traceId;

    /** 0-已结束 1-进行中 2-已中断 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
