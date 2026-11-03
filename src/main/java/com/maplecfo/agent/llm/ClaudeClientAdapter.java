package com.maplecfo.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter pattern: the "adapter". Translates the app's simple {@link ChatMessage}
 * / {@link ToolCall} objects to and from Anthropic's Messages API JSON, so no
 * other class knows Claude's request format.
 *
 * <p>Follows the current API documentation: the key is sent as
 * {@code Authorization: Bearer}, {@code anthropic-version: 2023-06-01}; an
 * {@code anthropic-workspace-id} header is added when configured (needed for
 * keys that are not tied to one workspace); no temperature/top_p is sent; and
 * the assistant's content blocks are sent back unchanged with tool results.
 */
public class ClaudeClientAdapter implements LlmClient {

    static final URI MESSAGES_URI = URI.create("https://api.anthropic.com/v1/messages");
    static final String API_VERSION = "2023-06-01";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final String workspaceId;
    private final HttpTransport transport;
    private final int maxTokens;
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();

    public ClaudeClientAdapter(String apiKey, String model, Optional<String> workspaceId, HttpTransport transport,
                               int maxTokens) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = Objects.requireNonNull(model, "model");
        this.workspaceId = workspaceId.orElse(null);
        this.transport = Objects.requireNonNull(transport, "transport");
        this.maxTokens = maxTokens;
    }

    public ClaudeClientAdapter(String apiKey, String model, Optional<String> workspaceId) {
        this(apiKey, model, workspaceId, HttpTransport.javaHttpClient(Duration.ofSeconds(60)), 4096);
    }

    @Override
    public LlmResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        String body = buildRequest(messages, tools).toString();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("anthropic-version", API_VERSION);
        headers.put("content-type", "application/json");
        if (workspaceId != null) {
            headers.put("anthropic-workspace-id", workspaceId);
        }
        LlmException last = null;
        for (int attempt = 0; attempt < 2; attempt++) { // one retry on temporary errors (decision D9)
            try {
                HttpTransport.Response r = transport.post(MESSAGES_URI, headers, body);
                requests.incrementAndGet();
                if (r.status() == 200) {
                    return parseResponse(r.body());
                }
                last = errorFor(r);
            } catch (IOException e) {
                last = new LlmException("Could not reach the Claude API: " + e.getMessage(), e, true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("The request to Claude was interrupted", e, false);
            }
            if (!last.isRetryable()) {
                break;
            }
            sleepBeforeRetry();
        }
        throw last;
    }

    ObjectNode buildRequest(List<ChatMessage> messages, List<ToolSchema> tools) {
        ObjectNode req = JSON.createObjectNode();
        req.put("model", model);
        req.put("max_tokens", maxTokens);
        StringBuilder system = new StringBuilder();
        ArrayNode out = req.putArray("messages");
        ArrayNode pendingResults = null;
        for (ChatMessage m : messages) {
            if (m.role() == ChatMessage.Role.TOOL) {
                if (pendingResults == null) {
                    ObjectNode user = out.addObject();
                    user.put("role", "user");
                    pendingResults = user.putArray("content");
                }
                ObjectNode tr = pendingResults.addObject();
                tr.put("type", "tool_result");
                tr.put("tool_use_id", m.toolCallId());
                tr.put("content", m.content());
                if (m.isError()) {
                    tr.put("is_error", true);
                }
                continue;
            }
            pendingResults = null;
            switch (m.role()) {
                case SYSTEM -> system.append(system.length() > 0 ? "\n\n" : "").append(m.content());
                case USER -> {
                    ObjectNode u = out.addObject();
                    u.put("role", "user");
                    u.put("content", m.content());
                }
                case ASSISTANT -> out.add(assistantMessage(m));
                default -> throw new IllegalStateException();
            }
        }
        if (system.length() > 0) {
            req.put("system", system.toString());
        }
        if (!tools.isEmpty()) {
            ArrayNode ts = req.putArray("tools");
            for (ToolSchema t : tools) {
                ObjectNode o = ts.addObject();
                o.put("name", t.name());
                o.put("description", t.description());
                o.set("input_schema", t.inputSchema());
            }
        }
        return req;
    }

    private ObjectNode assistantMessage(ChatMessage m) {
        ObjectNode a = JSON.createObjectNode();
        a.put("role", "assistant");
        if (m.rawContent() != null) {
            try {
                a.set("content", JSON.readTree(m.rawContent()));
                return a;
            } catch (IOException e) {
                // fall through and rebuild the blocks
            }
        }
        ArrayNode content = a.putArray("content");
        if (!m.content().isBlank()) {
            content.addObject().put("type", "text").put("text", m.content());
        }
        for (ToolCall c : m.toolCalls()) {
            ObjectNode tu = content.addObject();
            tu.put("type", "tool_use");
            tu.put("id", c.id());
            tu.put("name", c.name());
            tu.set("input", c.arguments());
        }
        return a;
    }

    LlmResponse parseResponse(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode content = root.path("content");
            StringBuilder text = new StringBuilder();
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode block : content) {
                switch (block.path("type").asText()) {
                    case "text" -> text.append(text.length() > 0 ? "\n" : "").append(block.path("text").asText());
                    case "tool_use" -> calls.add(new ToolCall(block.path("id").asText(), block.path("name").asText(),
                            block.path("input")));
                    default -> { /* thinking and other blocks are kept in rawContent only */ }
                }
            }
            int in = root.path("usage").path("input_tokens").asInt(0);
            int out = root.path("usage").path("output_tokens").asInt(0);
            inputTokens.addAndGet(in);
            outputTokens.addAndGet(out);
            return new LlmResponse(text.toString(), calls, root.path("stop_reason").asText("end_turn"),
                    content.toString(), in, out);
        } catch (IOException e) {
            throw new LlmException("Claude returned a response that could not be read", e, false);
        }
    }

    private LlmException errorFor(HttpTransport.Response r) {
        String detail = "";
        try {
            detail = JSON.readTree(r.body()).path("error").path("message").asText("");
        } catch (IOException ignored) {
            // keep the generic message
        }
        int s = r.status();
        if (s == 401 || s == 403) {
            return new LlmException("Claude rejected the API key (" + s + "). Check LLM_API_KEY in your .env file.", false);
        }
        if (s == 400 && detail.contains("anthropic-workspace-id")) {
            return new LlmException("This API key needs a workspace. Add LLM_WORKSPACE_ID=<id from "
                    + "platform.claude.com/settings/workspaces> to your .env file.", false);
        }
        if (s == 404 && detail.toLowerCase().contains("model")) {
            return new LlmException("Model '" + model + "' was not found. Set LLM_MODEL in .env. " + detail, false);
        }
        if (s == 429 || s == 529 || s >= 500) {
            return new LlmException("Claude is busy right now (" + s + "). Please try again.", true);
        }
        return new LlmException("Claude returned an error (" + s + "): " + detail, false);
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public UsageTotals usage() {
        return new UsageTotals(requests.get(), inputTokens.get(), outputTokens.get());
    }
}
