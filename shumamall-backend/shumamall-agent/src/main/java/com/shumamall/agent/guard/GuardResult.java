package com.shumamall.agent.guard;

import com.shumamall.agent.planning.PlanStep;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 高风险操作检查结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuardResult {

    /** 是否通过 */
    private boolean passed;

    /** 待确认的高风险步骤 */
    private List<PlanStep> pendingSteps;

    /** 面向用户的确认话术 */
    private String confirmMessage;

    public static GuardResult pass() {
        return new GuardResult(true, List.of(), null);
    }

    public static GuardResult block(List<PlanStep> pendingSteps, String confirmMessage) {
        return new GuardResult(false, pendingSteps, confirmMessage);
    }
}
