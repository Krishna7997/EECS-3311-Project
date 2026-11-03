package com.maplecfo.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.agent.llm.LlmException;
import com.maplecfo.agent.llm.LlmResponse;
import com.maplecfo.agent.llm.MockLlmClient;
import com.maplecfo.agent.llm.ToolCall;
import com.maplecfo.agent.llm.ToolSchema;
import com.maplecfo.agent.tools.DemoRules;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The agent loop with a scripted model: tool use, grounding, proposals, limits and failures. */
class CfoAgentTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock NOV_2026 = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Toronto"));

    @TempDir
    Path dir;

    private MapleCfoFacade app(LlmClient llm) {
        MapleCfoFacade f = AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")), NOV_2026, llm);
        f.saveProfile(new UserProfile(2003, "ON", 2010, null, null, null));
        return f;
    }

    private static ToolCall call(String id, String tool, String argsJson) throws Exception {
        return new ToolCall(id, tool, JSON.readTree(argsJson));
    }

    @Test
    void usesAToolAndGivesAGroundedAnswer() throws Exception {
        MockLlmClient llm = MockLlmClient.scripted(
                LlmResponse.tools(List.of(call("t1", "contribution_room", "{\"account\":\"TFSA\"}"))),
                LlmResponse.text("You have $39,500.00 of TFSA room this year."));
        AgentResponse r = app(llm).askCfo("How much TFSA room do I have?");
        assertTrue(r.grounded());
        assertEquals(List.of("contribution_room"), r.toolsUsed());
        assertEquals("You have $39,500.00 of TFSA room this year.", r.answer());

        List<ChatMessage> second = llm.received().get(1);
        ChatMessage toolMsg = second.get(second.size() - 1);
        assertEquals(ChatMessage.Role.TOOL, toolMsg.role());
        assertTrue(toolMsg.content().contains("39,500.00"), "the model sees the tool's numbers");
    }

    @Test
    void inventedAmountTriggersOneCorrection() throws Exception {
        MockLlmClient llm = MockLlmClient.scripted(
                LlmResponse.tools(List.of(call("t1", "contribution_room", "{\"account\":\"TFSA\"}"))),
                LlmResponse.text("You have about $50,000.00 of room."),
                LlmResponse.text("You have $39,500.00 of TFSA room."));
        AgentResponse r = app(llm).askCfo("TFSA room?");
        assertTrue(r.grounded());
        assertEquals("You have $39,500.00 of TFSA room.", r.answer());
        String correction = llm.received().get(2).get(llm.received().get(2).size() - 1).content();
        assertTrue(correction.contains("$50,000.00"));
    }

    @Test
    void stillInventedAfterCorrectionIsFlagged() throws Exception {
        MockLlmClient llm = MockLlmClient.scripted(
                LlmResponse.tools(List.of(call("t1", "contribution_room", "{\"account\":\"TFSA\"}"))),
                LlmResponse.text("You have $50,000.00."),
                LlmResponse.text("Still $50,000.00."));
        AgentResponse r = app(llm).askCfo("TFSA room?");
        assertFalse(r.grounded());
        assertTrue(r.answer().contains("could not verify $50,000.00"));
    }

    @Test
    void amountsFromTheQuestionAreAllowed() {
        MockLlmClient llm = MockLlmClient.scripted(LlmResponse.text("A $2,000 trip is a big expense; let me check first."));
        assertTrue(app(llm).askCfo("Can I afford a $2,000 trip?").grounded());
    }

    @Test
    void proposedChangeWaitsForConfirmationAndCanBeUndone() throws Exception {
        MockLlmClient llm = MockLlmClient.scripted(
                LlmResponse.tools(List.of(call("t1", "propose_action",
                        "{\"action\":\"set_budget\",\"category\":\"DINING\",\"amount\":200}"))),
                LlmResponse.text("I can set your dining budget for you. Please confirm."));
        MapleCfoFacade f = app(llm);
        AgentResponse r = f.askCfo("Set my dining budget to 200");
        assertEquals("A1", r.pendingActionId());
        assertTrue(f.getBudgetStatus(YearMonth.of(2026, 11)).isEmpty(), "nothing changes before confirmation");

        f.confirmProposedAction("A1");
        assertEquals(Category.DINING, f.getBudgetStatus(YearMonth.of(2026, 11)).get(0).budget().category());
        assertTrue(f.undoLastEdit());
        assertTrue(f.getBudgetStatus(YearMonth.of(2026, 11)).isEmpty());
        assertThrows(java.util.NoSuchElementException.class, () -> f.confirmProposedAction("A1"));
    }

    @Test
    void dismissedProposalChangesNothing() throws Exception {
        MockLlmClient llm = MockLlmClient.scripted(
                LlmResponse.tools(List.of(call("t1", "propose_action",
                        "{\"action\":\"set_budget\",\"category\":\"TRAVEL\",\"amount\":500}"))),
                LlmResponse.text("Proposed."));
        MapleCfoFacade f = app(llm);
        f.askCfo("budget travel 500");
        assertTrue(f.dismissProposedAction("A1"));
        assertTrue(f.getPendingActions().isEmpty());
        assertTrue(f.getBudgetStatus(YearMonth.of(2026, 11)).isEmpty());
    }

    @Test
    void invalidToolArgumentsAreReportedToTheModelNotExecuted() throws Exception {
        MockLlmClient llm = MockLlmClient.scripted(
                LlmResponse.tools(List.of(call("t1", "contribution_room", "{\"account\":\"PENSION\"}"),
                        call("t2", "no_such_tool", "{}"))),
                LlmResponse.text("Sorry, I couldn't check that account."));
        app(llm).askCfo("pension room?");
        List<ChatMessage> sent = llm.received().get(1);
        ChatMessage bad = sent.get(sent.size() - 2);
        ChatMessage unknown = sent.get(sent.size() - 1);
        assertTrue(bad.isError() && bad.content().contains("must be one of"));
        assertTrue(unknown.isError() && unknown.content().contains("Unknown tool"));
    }

    @Test
    void stopsAfterMaxSteps() throws Exception {
        List<LlmResponse> loop = new ArrayList<>();
        for (int i = 0; i < CfoAgent.MAX_STEPS + 2; i++) {
            loop.add(LlmResponse.tools(List.of(call("t" + i, "budget_status", "{}"))));
        }
        AgentResponse r = app(MockLlmClient.scripted(loop.toArray(LlmResponse[]::new))).askCfo("loop forever");
        assertTrue(r.answer().contains("couldn't finish"));
        assertEquals(List.of("budget_status"), r.toolsUsed());
    }

    @Test
    void modelOutageGivesAFriendlyMessage() {
        LlmClient down = new LlmClient() {
            public LlmResponse chat(List<ChatMessage> m, List<ToolSchema> t) { throw new LlmException("Claude is busy right now (529).", true); }
            public String modelName() { return "down"; }
        };
        assertTrue(app(down).askCfo("hi").answer().startsWith("The AI model is unavailable right now"));
    }

    @Test
    void remembersEarlierTurns() {
        MockLlmClient llm = MockLlmClient.scripted(LlmResponse.text("First answer."), LlmResponse.text("Second answer."));
        MapleCfoFacade f = app(llm);
        f.askCfo("first question");
        f.askCfo("follow-up");
        List<ChatMessage> second = llm.received().get(1);
        assertEquals("first question", second.get(1).content());
        assertEquals("First answer.", second.get(2).content());
    }

    @Test
    void demoModeAnswersFromToolSummariesWithoutAnApiKey() {
        MapleCfoFacade f = app(new MockLlmClient(DemoRules.forMapleCfo()));
        AgentResponse r = f.askCfo("How much TFSA room do I have?");
        assertTrue(r.answer().contains("$39,500.00"), r.answer());
        assertTrue(r.grounded());
        AgentResponse check = f.askCfo("Can I put $45,000 in my TFSA?");
        assertTrue(check.answer().contains("over your room"), check.answer());
        assertTrue(f.getLlm().isMock());
    }
}
