package com.shumamall.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Agent 异步任务线程池配置。
 * <p>
 * SSE 流式接口需要请求线程快速归还，"规划 + 工具执行 + LLM 订阅"在独立线程池中执行，
 * 避免同步调用长时间占用 Tomcat 请求线程。
 */
@Configuration
public class AgentExecutorConfig {

    /**
     * Agent 编排异步执行线程池。
     * <p>
     * 核心线程数根据服务实例 CPU 核数估算，最大线程数 + 队列长度按 200 并发内可接受排队设计。
     * 超出队列时由调用方线程执行（CallerRunsPolicy），避免直接丢弃请求。
     */
    @Bean("agentExecutor")
    public ExecutorService agentExecutor() {
        int coreSize = Runtime.getRuntime().availableProcessors() * 2;
        int maxSize = coreSize * 2;
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
