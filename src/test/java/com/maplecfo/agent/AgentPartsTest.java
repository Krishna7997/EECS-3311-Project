package com.maplecfo.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.llm.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentPartsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ResponseValidator validator = new ResponseValidator();

    @Test
    void validatorMatchesAmountsWrittenDifferently() {
        List<String> evidence = List.of("{\"available_room\":\"$1,234.50\",\"total\":\"2000\"}");
        assertTrue(validator.validate("You have $1,234.5 left and $2,000.00 in total.", evidence).grounded());
        ResponseValidator.ValidationReport r = validator.validate("About $999.99 is left.", evidence);
        assertFalse(r.grounded());
        assertEquals(List.of("$999.99"), r.unsupportedAmounts());
    }

    @Test
    void answersWithoutDollarAmountsAreGrounded() {
        assertTrue(validator.validate("Please set your birth year first.", List.of()).grounded());
    }

    @Test
    void disclaimerOnlyWhenInvestingIsMentioned() {
        assertTrue(validator.appendDisclaimer("Consider index ETFs.").contains("not financial advice"));
        assertEquals("Your budget is fine.", validator.appendDisclaimer("Your budget is fine."));
    }

    @Test
    void memoryKeepsTheLastTurnsAndStartsAtAQuestion() {
        ConversationMemory m = new ConversationMemory(2);
        for (int i = 1; i <= 3; i++) {
            m.add(ChatMessage.user("q" + i));
            m.add(ChatMessage.assistant("a" + i));
        }
        assertEquals(4, m.size());
        assertEquals("q2", m.all().get(0).content());
        assertThrows(IllegalArgumentException.class, () -> m.add(ChatMessage.toolResult("x", "{}", false)));
    }

    /** A tiny tool to check the Template Method's validation. */
    static class EchoTool extends AbstractFinanceTool {
        int runs;
        public String name() { return "echo"; }
        public String description() { return "echo"; }
        public JsonNode schema() { return schemaOf("account:string!:TFSA|RRSP:acct", "amount:number::amount"); }
        protected ObjectNode doExecute(JsonNode args) { runs++; return obj().put("summary", "ok"); }
    }

    @Test
    void templateMethodValidatesBeforeRunning() throws Exception {
        EchoTool t = new EchoTool();
        assertTrue(t.execute("1", JSON.readTree("{\"account\":\"tfsa\",\"amount\":5}")).success());
        assertEquals(1, t.runs);
        ToolResult missing = t.execute("2", JSON.readTree("{}"));
        ToolResult wrongType = t.execute("3", JSON.readTree("{\"account\":\"TFSA\",\"amount\":\"five\"}"));
        ToolResult negative = t.execute("4", JSON.readTree("{\"account\":\"TFSA\",\"amount\":-5}"));
        ToolResult extra = t.execute("5", JSON.readTree("{\"account\":\"TFSA\",\"hack\":true}"));
        assertFalse(missing.success() || wrongType.success() || negative.success() || extra.success());
        assertTrue(missing.toJson().contains("missing 'account'"));
        assertEquals(1, t.runs, "doExecute never runs with invalid arguments");
    }

    @Test
    void pendingActionsAreTakenOnce() {
        PendingActionStore s = new PendingActionStore();
        String id = s.add(new com.maplecfo.command.UndoableCommand() {
            public void execute() { }
            public void undo() { }
            public String describe() { return "x"; }
        });
        assertTrue(s.take(id.toLowerCase()).isPresent());
        assertTrue(s.take(id).isEmpty());
    }

    @Test
    void demoRulesUnderstandMonthNames() {
        assertEquals("2026-10", com.maplecfo.agent.tools.DemoRules.monthArgs("how much did i spend in october 2026").path("month").asText());
        assertFalse(com.maplecfo.agent.tools.DemoRules.monthArgs("how much did i spend").has("month"));
    }
}
