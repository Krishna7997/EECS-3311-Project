package com.maplecfo.agent.llm;

import java.util.List;
import java.util.Objects;

/**
 * One message in a conversation with the model.
 *
 * @param toolCalls     tools the assistant asked to run (ASSISTANT messages only)
 * @param toolCallId    which tool call this message answers (TOOL messages only)
 * @param rawContent    the assistant's original content blocks as JSON, sent back
 *                      unchanged so provider-specific blocks (e.g. thinking) survive
 */
public record ChatMessage(Role role, String content, List<ToolCall> toolCalls, String toolCallId,
                          boolean isError, String rawContent) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatMessage system(String text) {
        return new ChatMessage(Role.SYSTEM, text, List.of(), null, false, null);
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text, List.of(), null, false, null);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, text, List.of(), null, false, null);
    }

    public static ChatMessage assistant(LlmResponse r) {
        return new ChatMessage(Role.ASSISTANT, r.text(), r.toolCalls(), null, false, r.rawAssistantContent());
    }

    public static ChatMessage toolResult(String toolCallId, String json, boolean isError) {
        return new ChatMessage(Role.TOOL, json, List.of(), toolCallId, isError, null);
    }
}
