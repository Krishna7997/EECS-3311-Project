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

    /** Each reply is a String (text answer), an LlmResponse (e.g. tool calls) or an LlmException (thrown). */
    public FakeLlm(Object... replies) {
        this.replies.addAll(List.of(replies));
    }

    /** Adds replies after the ones given so far (e.g. after an import that asked about unknown shops). */
    public void then(Object... more) {
        replies.clear();
        replies.addAll(List.of(more));
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        received.add(messages);
        Object r = replies.isEmpty() ? "" : replies.poll();
        if (r instanceof LlmException e) throw e;
        if (r instanceof LlmResponse resp) return resp;
        return LlmResponse.text((String) r);
    }

    @Override
    public String modelName() {
        return "fake-model";
    }
}
