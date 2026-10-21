package com.antifraud.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工具调用日志表 (t_tool_call_log) - Function Calling 审计
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_tool_call_log")
public class ToolCallLog extends Model<ToolCallLog> {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String traceId;

    /** ROUTER/ANALYSIS/ACTION */
    private String agentName;

    private String toolName;

    /** 调用参数(JSON) */
    private String arguments;

    /** 返回值(JSON) */
    private String result;

    /** 0-失败 1-成功 */
    private Integer success;

    private String errorMsg;

    private Integer durationMs;

    private LocalDateTime createTime;
}
