package com.maplecfo.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the request JSON and response parsing against recorded API messages (no network). */
class ClaudeClientAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Records what was sent and replays canned responses. */
    static class FakeTransport implements HttpTransport {
        final List<Map<String, String>> headers = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        final List<Response> replies;

        FakeTransport(Response... replies) {
            this.replies = new ArrayList<>(List.of(replies));
        }

        @Override
        public Response post(URI uri, Map<String, String> h, String body) throws IOException {
            headers.add(h);
            bodies.add(body);
            if (replies.isEmpty()) throw new IOException("no more replies");
            return replies.remove(0);
        }
    }

    private static final String TOOL_USE_REPLY = """
            {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5",
             "content":[{"type":"thinking","thinking":"","signature":"sig123"},
                        {"type":"text","text":"Let me check your TFSA room."},
                        {"type":"tool_use","id":"toolu_1","name":"contribution_room","input":{"account":"TFSA"}}],
             "stop_reason":"tool_use","usage":{"input_tokens":120,"output_tokens":30}}""";

    private static final String FINAL_REPLY = """
            {"content":[{"type":"text","text":"You have $39,500.00 of TFSA room."}],
             "stop_reason":"end_turn","usage":{"input_tokens":200,"output_tokens":15}}""";

    private static ToolSchema roomTool() throws IOException {
        return new ToolSchema("contribution_room", "TFSA/RRSP/FHSA room",
                JSON.readTree("{\"type\":\"object\",\"properties\":{\"account\":{\"type\":\"string\"}}}"));
    }

    @Test
    void sendsDocumentedHeadersAndNoSamplingParameters() throws Exception {
        FakeTransport t = new FakeTransport(new HttpTransport.Response(200, FINAL_REPLY));
        ClaudeClientAdapter c = new ClaudeClientAdapter("sk-test", "claude-haiku-5-5", Optional.of("wrkspc_1"), t, 1024);
        c.chat(List.of(ChatMessage.system("You are MapleCFO."), ChatMessage.user("hi")), List.of(roomTool()));

        Map<String, String> h = t.headers.get(0);
        assertEquals("Bearer sk-test", h.get("Authorization"));
        assertEquals("2023-06-01", h.get("anthropic-version"));
        assertEquals("wrkspc_1", h.get("anthropic-workspace-id"));

        JsonNode req = JSON.readTree(t.bodies.get(0));
        assertEquals("claude-haiku-5-5", req.path("model").asText());
        assertEquals("You are MapleCFO.", req.path("system").asText());
        assertEquals("user", req.path("messages").get(0).path("role").asText());
        assertEquals("contribution_room", req.path("tools").get(0).path("name").asText());
        assertFalse(req.has("temperature"), "Haiku 5.5 docs say to omit temperature");
    }

    @Test
    void parsesToolCallsAndSendsBlocksBackWithResults() throws Exception {
        FakeTransport t = new FakeTransport(new HttpTransport.Response(200, TOOL_USE_REPLY),
                new HttpTransport.Response(200, FINAL_REPLY));
        ClaudeClientAdapter c = new ClaudeClientAdapter("k", "m", Optional.empty(), t, 1024);

        List<ChatMessage> convo = new ArrayList<>(List.of(ChatMessage.user("How much TFSA room do I have?")));
        LlmResponse first = c.chat(convo, List.of(roomTool()));
        assertEquals("tool_use", first.stopReason());
        assertEquals("contribution_room", first.toolCalls().get(0).name());
        assertEquals("TFSA", first.toolCalls().get(0).arguments().path("account").asText());

        convo.add(ChatMessage.assistant(first));
        convo.add(ChatMessage.toolResult("toolu_1", "{\"available\":\"$39,500.00\"}", false));
        LlmResponse second = c.chat(convo, List.of(roomTool()));
        assertEquals("You have $39,500.00 of TFSA room.", second.text());

        JsonNode req = JSON.readTree(t.bodies.get(1));
        JsonNode assistant = req.path("messages").get(1);
        assertEquals("thinking", assistant.path("content").get(0).path("type").asText(),
                "thinking block must be sent back unchanged");
        JsonNode toolResult = req.path("messages").get(2).path("content").get(0);
        assertEquals("tool_result", toolResult.path("type").asText());
        assertEquals("toolu_1", toolResult.path("tool_use_id").asText());

        assertEquals(new LlmClient.UsageTotals(2, 320, 45), c.usage());
    }

    @Test
    void retriesOnceWhenBusyThenSucceeds() {
        FakeTransport t = new FakeTransport(new HttpTransport.Response(529, "{\"error\":{\"message\":\"Overloaded\"}}"),
                new HttpTransport.Response(200, FINAL_REPLY));
        LlmResponse r = new ClaudeClientAdapter("k", "m", Optional.empty(), t, 1024).chat(List.of(ChatMessage.user("x")), List.of());
        assertTrue(r.text().contains("39,500"));
        assertEquals(2, t.bodies.size());
    }

    @Test
    void clearMessagesForCommonErrors() {
        LlmException badKey = assertThrows(LlmException.class, () ->
                new ClaudeClientAdapter("k", "m", Optional.empty(),
                        new FakeTransport(new HttpTransport.Response(401, "{}")), 1024).chat(List.of(ChatMessage.user("x")), List.of()));
        assertTrue(badKey.getMessage().contains("LLM_API_KEY"));
        assertFalse(badKey.isRetryable());

        String ws = "{\"error\":{\"message\":\"This API key is not scoped to a workspace, so this request must include the anthropic-workspace-id header\"}}";
        LlmException noWs = assertThrows(LlmException.class, () ->
                new ClaudeClientAdapter("k", "m", Optional.empty(),
                        new FakeTransport(new HttpTransport.Response(400, ws)), 1024).chat(List.of(ChatMessage.user("x")), List.of()));
        assertTrue(noWs.getMessage().contains("LLM_WORKSPACE_ID"));
    }

    @Test
    void networkFailureIsReportedAfterOneRetry() {
        FakeTransport t = new FakeTransport();
        LlmException e = assertThrows(LlmException.class, () ->
                new ClaudeClientAdapter("k", "m", Optional.empty(), t, 1024).chat(List.of(ChatMessage.user("x")), List.of()));
        assertTrue(e.getMessage().startsWith("Could not reach"));
        assertEquals(2, t.bodies.size());
    }
}
