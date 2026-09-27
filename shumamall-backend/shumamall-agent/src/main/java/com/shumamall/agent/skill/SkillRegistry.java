package com.shumamall.agent.skill;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 技能注册表。
 * <p>
 * 持有全部 {@link AgentSkill} Bean，提供按 ID 查找与批量描述能力。
 * 新增技能只需实现 {@link AgentSkill} 并注册为 Spring Bean，注册表自动收录。
 */
@Component
public class SkillRegistry {

    private final Map<String, AgentSkill> skillsById;

    public SkillRegistry(List<AgentSkill> skills) {
        this.skillsById = skills.stream()
                .collect(Collectors.toMap(AgentSkill::getSkillId, Function.identity()));
    }

    /**
     * 按技能 ID 查找。
     *
     * @return 未找到时返回 null
     */
    public AgentSkill findById(String skillId) {
        return skillsById.get(skillId);
    }

    /**
     * 全部技能列表。
     */
    public List<AgentSkill> all() {
        return List.copyOf(skillsById.values());
    }

    /**
     * 生成技能清单描述（供 LLM 路由时选择）。
     */
    public String describeAll() {
        return all().stream()
                .map(skill -> "- " + skill.getSkillId() + "：" + skill.getSkillName() + "。" + skill.getDescription())
                .collect(Collectors.joining("\n"));
    }
}
