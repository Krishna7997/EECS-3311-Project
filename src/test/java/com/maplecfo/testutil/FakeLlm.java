package com.maplecfo.testutil;

import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.agent.llm.LlmException;
import com.maplecfo.agent.llm.LlmResponse;
import com.maplecfo.agent.llm.ToolSchema;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A scripted "real" model (isMock() is false) for tests of AI-only code paths. */
public class FakeLlm implements LlmClient {

    private final Deque<Object> replies = new ArrayDeque<>();
    public final List<List<ChatMessage>> received = new ArrayList<>();

    /** Each reply is a String (text answer) or an LlmException (thrown). */
    public FakeLlm(Object... replies) {
        this.replies.addAll(List.of(replies));
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        received.add(messages);
        Object r = replies.isEmpty() ? "" : replies.poll();
        if (r instanceof LlmException e) throw e;
        return LlmResponse.text((String) r);
    }

    @Override
    public String modelName() {
        return "fake-model";
    }
}
