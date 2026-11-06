package com.maplecfo.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.llm.LlmResponse;
import com.maplecfo.agent.llm.ToolCall;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.planning.BudgetPlanner;
import com.maplecfo.testutil.FakeLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** F17: the AI monthly review gathers figures with several tools and proposes next month's budgets. */
class MonthlyReviewTest {

    private static final Clock NOV6 = Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneId.of("America/Toronto"));
    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    private MapleCfoFacade withHistory(MapleCfoFacade app) throws Exception {
        app.importTransactionsAuto(Path.of("sample-data/demo/td_chequing_2025-2026.csv"), "chequing");
        return app;
    }

    @Test
    void budgetSuggestionsUseTheThreeMonthsBeforeRoundedUp() throws Exception {
        MapleCfoFacade app = withHistory(AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")), NOV6));
        app.setBudget(Category.GROCERIES, Money.of(450), NOV);
        List<BudgetPlanner.Suggestion> list = app.suggestBudgets(NOV);
        assertFalse(list.isEmpty());
        for (BudgetPlanner.Suggestion s : list) {
            assertFalse(List.of(Category.RENT, Category.INCOME, Category.TRANSFER, Category.UNCATEGORIZED).contains(s.category()));
            assertEquals(0, s.suggested().getAmount().remainder(java.math.BigDecimal.TEN).signum(), s.toString());
            assertFalse(s.suggested().isLessThan(s.threeMonthAverage()));
            assertTrue(s.suggested().minus(s.threeMonthAverage()).isLessThan(Money.of(10)));
        }
        for (int i = 1; i < list.size(); i++) {
            assertFalse(list.get(i).threeMonthAverage().isGreaterThan(list.get(i - 1).threeMonthAverage()), "biggest first");
        }
        BudgetPlanner.Suggestion groceries = list.stream().filter(s -> s.category() == Category.GROCERIES).findFirst().orElseThrow();
        assertEquals(Money.of(450), groceries.current());
    }

    @Test
    void demoModeRunsTheSameToolsInAFixedOrderAndProposalsCanBeConfirmed() throws Exception {
        MapleCfoFacade app = withHistory(AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")), NOV6));
        MonthlyReviewService.MonthlyReview r = app.runMonthlyReview(OCT);
        assertFalse(r.byAi());
        assertTrue(r.text().startsWith("Review of 2026-10"), r.text());
        assertTrue(r.toolsUsed().containsAll(List.of("spending_summary", "budget_status", "recurring_charges",
                "propose_action")), r.toolsUsed().toString());
        assertFalse(r.proposals().isEmpty());
        assertTrue(r.proposals().size() <= MonthlyReviewService.MAX_PROPOSALS);
        assertTrue(r.proposals().values().stream().allMatch(d -> d.contains("2026-11")));

        String id = r.proposals().keySet().iterator().next();
        Category c = Category.valueOf(r.proposals().get(id).replaceAll(".*Set (\\w+) budget.*", "$1"));
        assertTrue(app.getBudgetStatus(NOV).isEmpty(), "nothing applied yet");
        app.confirmProposedAction(id);
        Money limit = app.getBudgetStatus(NOV).stream().filter(b -> b.budget().category() == c).findFirst()
                .orElseThrow().budget().limit();
        assertEquals(app.suggestBudgets(NOV).stream().filter(s -> s.category() == c).findFirst().orElseThrow().suggested(), limit);
        assertFalse(app.getPendingActions().containsKey(id));
    }

    @Test
    void theAiPlansSeveralStepsAndEveryProposalWaitsForTheUser() throws Exception {
        FakeLlm llm = new FakeLlm();
        MapleCfoFacade app = withHistory(AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")), NOV6, llm));
        llm.then(
                LlmResponse.tools(List.of(call("1", "spending_summary", "month", "2026-10"),
                        call("2", "budget_status", "month", "2026-10"), call("3", "suggest_budgets", "month", "2026-11"))),
                LlmResponse.tools(List.of(budget("4", "DINING", 450), budget("5", "SHOPPING", 120))),
                "October went over on dining.\n- Keep dining under $450.00 in November\n- The proposed budgets are "
                        + "waiting for your confirmation.");
        llm.received.clear();

        MonthlyReviewService.MonthlyReview r = app.runMonthlyReview(OCT);
        assertTrue(r.byAi());
        assertTrue(r.grounded(), r.text());
        assertEquals(List.of("spending_summary", "budget_status", "suggest_budgets", "propose_action"), r.toolsUsed());
        assertEquals(2, r.proposals().size());
        assertTrue(r.proposals().values().stream().anyMatch(d -> d.contains("DINING") && d.contains("$450.00")));
        assertTrue(app.getBudgetStatus(NOV).isEmpty(), "nothing is applied before the user confirms");
        String instruction = llm.received.get(0).get(1).content();
        assertTrue(instruction.contains("monthly money review for 2026-10") && instruction.contains("suggest_budgets for 2026-11"));
        assertTrue(app.getPendingActions().keySet().containsAll(r.proposals().keySet()));
    }

    private static ToolCall call(String id, String name, String key, String value) {
        ObjectNode a = JSON.createObjectNode();
        a.put(key, value);
        return new ToolCall(id, name, a);
    }

    private static ToolCall budget(String id, String category, int amount) {
        ObjectNode a = JSON.createObjectNode();
        a.put("action", "set_budget");
        a.put("category", category);
        a.put("amount", amount);
        a.put("month", "2026-11");
        return new ToolCall(id, "propose_action", a);
    }
}
