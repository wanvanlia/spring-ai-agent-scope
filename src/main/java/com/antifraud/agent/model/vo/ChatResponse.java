package com.antifraud.agent.model.vo;

import lombok.Data;

/**
 * 多轮对话响应
 */
@Data
public class ChatResponse {
    private String sessionId;
    private String conversationId;
    private String reply;
    /** 是否需要追问/转人工 */
    private boolean needFollowUp;
}
