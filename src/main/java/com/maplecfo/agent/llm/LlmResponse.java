package com.maplecfo.agent.llm;

import java.util.List;

/**
 * The model's reply.
 *
 * @param stopReason          e.g. "end_turn", "tool_use", "max_tokens", "refusal"
 * @param rawAssistantContent provider content blocks as JSON, kept for the next request
 */
public record LlmResponse(String text, List<ToolCall> toolCalls, String stopReason, String rawAssistantContent,
                          int inputTokens, int outputTokens) {

    public LlmResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LlmResponse text(String text) {
        return new LlmResponse(text, List.of(), "end_turn", null, 0, 0);
    }

    public static LlmResponse tools(List<ToolCall> calls) {
        return new LlmResponse("", calls, "tool_use", null, 0, 0);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
