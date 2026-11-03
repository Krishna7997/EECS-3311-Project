package com.maplecfo.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An offline stand-in for the LLM, used when no API key is configured and in
 * tests. It never invents numbers: it picks a tool with simple keyword rules,
 * then answers with the "summary" text the tool returned.
 *
 * <p>Tests can instead queue exact responses with {@link #scripted}.
 */
public class MockLlmClient implements LlmClient {

    /** Keyword rule: when the question matches, call this tool with these arguments. */
    public record Rule(Predicate<String> matches, String toolName, Function<String, ObjectNode> arguments) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<Rule> rules;
    private final Deque<LlmResponse> script = new ArrayDeque<>();
    private final List<List<ChatMessage>> received = new ArrayList<>();
    private int ids;

    public MockLlmClient(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** A mock that returns these responses in order, then falls back to "I don't know". */
    public static MockLlmClient scripted(LlmResponse... responses) {
        MockLlmClient m = new MockLlmClient(List.of());
        m.script.addAll(List.of(responses));
        return m;
    }

    @Override
    public synchronized LlmResponse chat(List<ChatMessage> messages, List<ToolSchema> tools) {
        received.add(List.copyOf(messages));
        if (!script.isEmpty()) {
            return script.poll();
        }
        ChatMessage last = messages.get(messages.size() - 1);
        if (last.role() == ChatMessage.Role.TOOL) {
            return LlmResponse.text(summarizeToolResults(messages));
        }
        String question = last.content().toLowerCase(Locale.ROOT);
        for (Rule r : rules) {
            boolean available = tools.stream().anyMatch(t -> t.name().equals(r.toolName()));
            if (available && r.matches().test(question)) {
                return LlmResponse.tools(List.of(new ToolCall("mock_" + (++ids), r.toolName(), r.arguments().apply(question))));
            }
        }
        return LlmResponse.text("I'm running in demo mode without an AI model, so I can only answer questions about "
                + "budgets, spending, subscriptions, TFSA/RRSP/FHSA room, FIRE, debt payoff, net worth, goals and "
                + "affordability. Add your API key to .env for full answers.");
    }

    private static String summarizeToolResults(List<ChatMessage> messages) {
        List<String> parts = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && messages.get(i).role() == ChatMessage.Role.TOOL; i--) {
            ChatMessage m = messages.get(i);
            String summary = m.content();
            try {
                JsonNode n = JSON.readTree(m.content());
                summary = n.path("summary").asText(n.path("error").asText(m.content()));
            } catch (Exception ignored) {
                // use the raw text
            }
            parts.add(0, summary);
        }
        return String.join("\n", parts);
    }

    @Override
    public String modelName() {
        return "mock (demo mode)";
    }

    @Override
    public boolean isMock() {
        return true;
    }

    /** Every conversation this mock received, for tests. */
    public synchronized List<List<ChatMessage>> received() {
        return List.copyOf(received);
    }

    public static ObjectNode args() {
        return JSON.createObjectNode();
    }
}
