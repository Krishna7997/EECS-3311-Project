package com.maplecfo.agent;

import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.agent.llm.LlmException;
import com.maplecfo.agent.llm.LlmResponse;
import com.maplecfo.agent.llm.ToolCall;
import com.maplecfo.domain.UserProfile;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * F10 AI CFO agent. A bounded reason-act loop: the model plans which finance
 * tools to call, the agent runs them, the model reads the results and either
 * calls more tools or answers. The final answer is checked by
 * {@link ResponseValidator} so it cannot contain invented dollar amounts.
 */
public class CfoAgent {

    public static final int MAX_STEPS = 6;

    private final LlmClient llm;
    private final ToolRegistry tools;
    private final ConversationMemory memory;
    private final PromptBuilder promptBuilder;
    private final ResponseValidator validator;
    private final PendingActionStore pendingActions;
    private final Supplier<UserProfile> profile;
    private final Clock clock;

    public CfoAgent(LlmClient llm, ToolRegistry tools, ConversationMemory memory, PromptBuilder promptBuilder,
                    ResponseValidator validator, PendingActionStore pendingActions, Supplier<UserProfile> profile,
                    Clock clock) {
        this.llm = Objects.requireNonNull(llm);
        this.tools = Objects.requireNonNull(tools);
        this.memory = Objects.requireNonNull(memory);
        this.promptBuilder = Objects.requireNonNull(promptBuilder);
        this.validator = Objects.requireNonNull(validator);
        this.pendingActions = Objects.requireNonNull(pendingActions);
        this.profile = Objects.requireNonNull(profile);
        this.clock = Objects.requireNonNull(clock);
    }

    public AgentResponse handle(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new AgentResponse("Please type a question.", List.of(), null, true);
        }
        String system = promptBuilder.buildSystemPrompt(profile.get(), LocalDate.now(clock));
        Turn t = runLoop(promptBuilder.buildMessages(system, memory, userMessage), MAX_STEPS);
        return finish(userMessage, t, true);
    }

    /**
     * A longer task such as the monthly review (F17): more steps are allowed because the model gathers data with
     * several tools, then proposes changes, then answers. It is not added to the chat history.
     */
    public AgentResponse runTask(String instruction, int maxSteps) {
        String system = promptBuilder.buildSystemPrompt(profile.get(), LocalDate.now(clock));
        List<ChatMessage> start = new ArrayList<>(List.of(ChatMessage.system(system), ChatMessage.user(instruction)));
        return finish(instruction, runLoop(start, maxSteps), false);
    }

    /** Bookkeeping for one user message. */
    private static final class Turn {
        List<ChatMessage> msgs;
        final Set<String> toolsUsed = new LinkedHashSet<>();
        final List<String> evidence = new ArrayList<>();
        String pendingActionId;
        final List<String> pendingActionIds = new ArrayList<>();
        String finalText;
        String failure;
    }

    private Turn runLoop(List<ChatMessage> start, int maxSteps) {
        Turn t = new Turn();
        List<ChatMessage> msgs = new ArrayList<>(start);
        t.msgs = msgs;
        try {
            for (int step = 0; step < maxSteps; step++) {
                LlmResponse r = llm.chat(msgs, tools.schemas());
                if (!r.hasToolCalls()) {
                    t.finalText = r.text();
                    return t;
                }
                msgs.add(ChatMessage.assistant(r));
                for (ToolCall call : r.toolCalls()) {
                    ToolResult result = tools.execute(call);
                    t.toolsUsed.add(call.name());
                    t.evidence.add(result.toJson());
                    if (result.success() && result.data().hasNonNull("action_id")) {
                        t.pendingActionId = result.data().get("action_id").asText();
                        t.pendingActionIds.add(t.pendingActionId);
                    }
                    msgs.add(ChatMessage.toolResult(call.id(), result.toJson(), !result.success()));
                }
            }
            t.failure = "I couldn't finish this within " + maxSteps + " steps. Try asking a more specific question.";
        } catch (LlmException e) {
            t.failure = "The AI model is unavailable right now: " + e.getMessage();
        }
        return t;
    }

    private AgentResponse finish(String userMessage, Turn t, boolean remember) {
        List<ChatMessage> msgs = t.msgs;
        if (t.failure != null) {
            return new AgentResponse(t.failure, List.copyOf(t.toolsUsed), t.pendingActionId, true, t.pendingActionIds);
        }
        List<String> evidence = new ArrayList<>(t.evidence);
        evidence.add(userMessage);
        String answer = t.finalText == null ? "" : t.finalText.trim();
        ResponseValidator.ValidationReport report = validator.validate(answer, evidence);

        if (!report.grounded()) {
            // One correction round: ask the model to answer again using only tool numbers.
            List<ChatMessage> retry = new ArrayList<>(msgs);
            retry.add(ChatMessage.assistant(answer));
            retry.add(ChatMessage.user("Your answer contains amounts that are not in any tool result: "
                    + String.join(", ", report.unsupportedAmounts())
                    + ". Rewrite the answer using only numbers from the tool results."));
            try {
                LlmResponse again = llm.chat(retry, List.of());
                ResponseValidator.ValidationReport second = validator.validate(again.text(), evidence);
                if (second.grounded() && !again.text().isBlank()) {
                    answer = again.text().trim();
                    report = second;
                }
            } catch (LlmException ignored) {
                // keep the first answer and flag it below
            }
        }
        if (!report.grounded()) {
            answer = answer + "\n\n(Note: I could not verify " + String.join(", ", report.unsupportedAmounts())
                    + " against your data, so please double-check those amounts.)";
        }
        answer = validator.appendDisclaimer(answer);
        if (remember) {
            memory.add(ChatMessage.user(userMessage));
            memory.add(ChatMessage.assistant(answer));
        }
        return new AgentResponse(answer, List.copyOf(t.toolsUsed), t.pendingActionId, report.grounded(),
                t.pendingActionIds);
    }

    public PendingActionStore getPendingActions() {
        return pendingActions;
    }

    public ConversationMemory getMemory() {
        return memory;
    }

    public LlmClient getLlm() {
        return llm;
    }

    public List<String> toolNames() {
        return tools.names();
    }
}
