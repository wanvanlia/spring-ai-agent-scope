package com.antifraud.agent.model.dto;

import lombok.Data;

/**
 * Router Agent 意图识别 + 号码槽位提取结果
 */
@Data
public class RouterResult {

    /** 意图: QUERY/REPORT/SHUTDOWN/UNKNOWN */
    private String intent;

    /** 从用户输入中提取的号码(可能为空 -> 触发 DATA_COLLECTION 追问) */
    private String phone;

    /** 是否信息缺失, 需追问 */
    private boolean missingInfo;

    /** 追问提示语 */
    private String followUp;
}
