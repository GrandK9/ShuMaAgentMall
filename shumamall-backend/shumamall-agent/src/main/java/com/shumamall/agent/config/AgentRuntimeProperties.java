package com.shumamall.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 运行时配置：提示词、记忆窗口、规划与 ReAct 参数；可由 Nacos {@code shumamall-agent.yaml} 覆盖并热刷新。
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "shumamall.agent")
public class AgentRuntimeProperties {

    private Prompts prompts = new Prompts();
    private Memory memory = new Memory();
    private Planning planning = new Planning();
    private React react = new React();
    private Context context = new Context();

    @Data
    public static class Prompts {
        /** skillId → system prompt；空则使用 {@link AgentPromptDefaults} */
        private Map<String, String> skills = new LinkedHashMap<>();
        /** 技能路由 user 模板，占位符见 {@link AgentPromptProvider} */
        private String skillRouterUserTemplate;
        /** 规划器 user 模板 */
        private String plannerUserTemplate;
        /** 规划器规则块（不含 maxSteps 行时可配合占位符 {maxSteps}） */
        private String plannerRules;
        /** 规划 JSON 结构示例（静态展示用） */
        private String plannerOutputFormatExample;
        private String summarySystem;
        private String summaryUserTemplate;
        private String reactRethinkPrefix;
    }

    @Data
    public static class Memory {
        private int maxMessages = 20;
        private long ttlMinutes = 30;
        private boolean summarizeEnabled = true;
        private int compressAfterRounds = 6;
        private int keepRecentMessages = 8;
    }

    @Data
    public static class Planning {
        private int maxSteps = 15;
    }

    @Data
    public static class React {
        private int maxRounds = 2;
    }

    @Data
    public static class Context {
        /** 注入规划/路由的近期原文条数上限 */
        private int planningMaxMessages = 12;
    }
}
