package com.shumamall.agent.skill;

import lombok.Data;

/**
 * 技能选择结果（LLM 结构化输出模型）。
 */
@Data
public class SkillSelection {

    /** 命中的技能 ID */
    private String skillId;

    /** 选择理由 */
    private String reason;
}
