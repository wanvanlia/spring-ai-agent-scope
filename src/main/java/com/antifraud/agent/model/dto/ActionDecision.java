package com.antifraud.agent.model.dto;

import lombok.Data;

/**
 * Action Agent 结构化处置决策(AgentScope 结构化输出)。
 *
 * <p>约定: 模型必须<b>先真正调用关停/通知工具</b>, 再按本结构回填执行结果;
 * 业务层据此落库工单状态, 保证"工单状态"与"实际执行"一致。
 */
@Data
public class ActionDecision {

    /** 是否已提交关停工单 */
    private boolean shutdown;

    /** 是否已向潜在受害者发送预警 */
    private boolean notifyVictim;

    /** 处置说明(未执行时说明原因, 如"高风险需人工复核") */
    private String reason;
}
