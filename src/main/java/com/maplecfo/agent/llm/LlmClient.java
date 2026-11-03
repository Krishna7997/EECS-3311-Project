package com.maplecfo.agent.llm;

import java.util.List;

/**
 * The only way the app talks to a language model. Adapter pattern: the
 * "target" interface. {@link ClaudeClientAdapter} adapts Anthropic's HTTP API
 * to it, and {@link MockLlmClient} answers without a network or key.
 */
public interface LlmClient {

    /**
     * @param messages the conversation; a leading SYSTEM message is the system prompt
     * @param tools    tools the model may ask to run (may be empty)
     * @throws LlmException when the model cannot be reached or rejects the request
     */
    LlmResponse chat(List<ChatMessage> messages, List<ToolSchema> tools);

    String modelName();

    /** True for the offline mock, so the UI can say "demo mode". */
    default boolean isMock() {
        return false;
    }

    /** Tokens used so far in this session (for the report's AI cost section). */
    default UsageTotals usage() {
        return new UsageTotals(0, 0, 0);
    }

    record UsageTotals(long requests, long inputTokens, long outputTokens) {
    }
}
