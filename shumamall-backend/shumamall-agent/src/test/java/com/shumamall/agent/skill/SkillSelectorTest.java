package com.shumamall.agent.skill;

import com.shumamall.agent.config.AgentPromptProvider;
import com.shumamall.agent.config.AgentRuntimeProperties;
import com.shumamall.agent.skill.skills.ShoppingGuideSkill;
import com.shumamall.agent.skill.skills.TradeSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 技能路由：LLM 失败时关键词兜底；显式 skillId 优先。
 */
class SkillSelectorTest {

    private SkillSelector selector;

    @BeforeEach
    void setUp() {
        AgentPromptProvider promptProvider = new AgentPromptProvider(new AgentRuntimeProperties());
        SkillRegistry registry = new SkillRegistry(java.util.List.of(
                new ShoppingGuideSkill(promptProvider),
                new TradeSkill(promptProvider)));
        ChatClient chatClient = mock(ChatClient.class);
        selector = new SkillSelector(registry, chatClient, promptProvider, false);
    }

    @Test
    void tradeKeywordsRouteToTradeWithoutLlm() {
        AgentSkill skill = selector.select("查看我的购物车", null);
        assertEquals("trade", skill.getSkillId());
    }

    @Test
    void shoppingQueryStaysOnGuideWhenNoTradeKeyword() {
        AgentSkill skill = selector.select("帮我搜索3000块左右的手机", null);
        assertEquals("shopping_guide", skill.getSkillId());
    }

    @Test
    void preferredSkillIdOverridesRouting() {
        AgentSkill skill = selector.select("随便聊聊", "trade");
        assertEquals("trade", skill.getSkillId());
    }

    @Test
    void shortFollowUpUsesContextForTrade() {
        String context = "ASSISTANT: 你的订单共 5 笔，目前全部为已取消状态\nUSER: 查查 ORD202607261545220626";
        AgentSkill skill = selector.select("再试试", null, context);
        assertEquals("trade", skill.getSkillId());
    }
}
