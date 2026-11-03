package com.maplecfo.agent;

import java.util.List;

/**
 * The agent's answer to one user message.
 *
 * @param pendingActionId set when the AI proposed a change the user can confirm
 * @param grounded        every dollar amount in the answer was found in a tool result
 */
public record AgentResponse(String answer, List<String> toolsUsed, String pendingActionId, boolean grounded) {

    public AgentResponse {
        toolsUsed = List.copyOf(toolsUsed);
    }

    public boolean hasPendingAction() {
        return pendingActionId != null;
    }
}
