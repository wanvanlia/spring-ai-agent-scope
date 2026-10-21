package com.antifraud.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智能反诈 Agent 系统主启动类。
 * <p>技术栈: Spring Boot 3.4 + AgentScope Java 2.x(智能体运行时) + Spring AI(向量/RAG)
 * + MyBatis-Plus + Redis + Milvus。
 */
@SpringBootApplication
@EnableAsync
@MapperScan("com.antifraud.agent.mapper")
public class AntiFraudAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AntiFraudAgentApplication.class, args);
    }
}
