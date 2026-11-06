package com.maplecfo.agent;

import java.util.List;

/**
 * The agent's answer to one user message.
 *
 * @param pendingActionId set when the AI proposed a change the user can confirm
 * @param grounded        every dollar amount in the answer was found in a tool result
 * @param pendingActionIds every change proposed in this answer (a monthly review can propose several)
 */
public record AgentResponse(String answer, List<String> toolsUsed, String pendingActionId, boolean grounded,
                            List<String> pendingActionIds) {

    public AgentResponse {
        toolsUsed = List.copyOf(toolsUsed);
        pendingActionIds = pendingActionIds == null ? (pendingActionId == null ? List.of() : List.of(pendingActionId))
                : List.copyOf(pendingActionIds);
    }

    public AgentResponse(String answer, List<String> toolsUsed, String pendingActionId, boolean grounded) {
        this(answer, toolsUsed, pendingActionId, grounded, null);
    }

    public boolean hasPendingAction() {
        return pendingActionId != null;
    }
}
