package com.shumamall.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Agent 服务启动类（端口 8088）。
 * <p>
 * 基于 Spring AI 实现：Skill 层（技能封装）+ 记忆模块 + 规划模块 + 工具调用模块，
 * 通过 Feign 调用 product / order / user 等后端微服务。
 */
@SpringBootApplication(scanBasePackages = "com.shumamall")
@EnableFeignClients(basePackages = "com.shumamall.agent.feign")
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
