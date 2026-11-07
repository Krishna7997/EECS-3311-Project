package com.maplecfo.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.agent.llm.LlmResponse;
import com.maplecfo.agent.llm.MockLlmClient;
import com.maplecfo.agent.llm.ToolCall;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Each finance tool the AI agent can call (F10), run exactly as the agent runs it: the model asks for
 * the tool, the tool answers with JSON. Checks the numbers and the argument validation.
 */
class FinanceToolsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock NOV_2026 = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Toronto"));

    @TempDir
    Path dir;

    /** A facade on the shared test database, used to set up data. */
    private MapleCfoFacade setup() {
        return AppContext.create(DatabaseManager.forFile(dir.resolve("t.db")), NOV_2026);
    }

    @BeforeEach
    void importSample() throws Exception {
        MapleCfoFacade f = setup();
        f.saveProfile(new UserProfile(2003, "ON", 2010, null, null, null));
        f.importTransactions(Path.of("sample-data/td_chequing_sample.csv"), "chequing", "TD bank account");
    }

    /** Runs one tool through the agent and returns what the tool sent back to the model. */
    private JsonNode run(String tool, String args) throws Exception {
        MockLlmClient llm = MockLlmClient.scripted(
                LlmResponse.tools(List.of(new ToolCall("t1", tool, JSON.readTree(args)))),
                LlmResponse.text("ok"));
        AppContext.create(DatabaseManager.forFile(dir.resolve("t.db")), NOV_2026, llm).askCfo("question");
        List<ChatMessage> second = llm.received().get(1);
        ChatMessage toolMsg = second.get(second.size() - 1);
        assertEquals(ChatMessage.Role.TOOL, toolMsg.role());
        return JSON.readTree(toolMsg.content());
    }

    private void assertError(JsonNode result, String expectedText) {
        assertTrue(result.has("error"), "expected an error but got " + result);
        assertTrue(result.get("error").asText().contains(expectedText), result.get("error").asText());
    }

    // ------------------------------------------------------------------ spending_summary

    @Test
    void spendingSummaryForAMonth() throws Exception {
        JsonNode r = run("spending_summary", "{\"month\":\"2026-10\"}");
        assertEquals("2026-10", r.get("month").asText());
        assertEquals("$2,256.64", r.get("total_spent").asText());
        assertEquals("$1,890.00", r.get("total_income").asText());
        assertTrue(r.get("summary").asText().contains("RENT"), r.toString());
    }

    @Test
    void spendingSummaryForAnEmptyMonthAndABadMonth() throws Exception {
        assertTrue(run("spending_summary", "{\"month\":\"2025-01\"}").get("summary").asText()
                .startsWith("No spending recorded for 2025-01"));
        assertTrue(run("spending_summary", "{\"month\":\"2026-13\"}").has("error"));
    }

    // ------------------------------------------------------------------ recurring_charges

    @Test
    void recurringChargesListsSubscriptions() throws Exception {
        JsonNode r = run("recurring_charges", "{}");
        List<String> merchants = r.get("charges").findValuesAsText("merchant");
        assertTrue(merchants.stream().anyMatch(m -> m.startsWith("NETFLIX")), merchants.toString());
        assertTrue(merchants.stream().anyMatch(m -> m.startsWith("SPOTIFY")), merchants.toString());
        for (JsonNode c : r.get("charges")) {
            if (c.get("merchant").asText().startsWith("SPOTIFY")) {
                assertTrue(c.get("price_increased").asBoolean(), "the sample raises Spotify's price");
                assertTrue(c.get("price_note").asText().startsWith("The price already went up"));
            }
        }
        assertTrue(r.get("total_yearly_cost").asText().startsWith("$"));
    }

    // ------------------------------------------------------------------ debt_payoff

    @Test
    void debtPayoffWithoutDebts() throws Exception {
        assertEquals("You have no debts recorded.", run("debt_payoff", "{}").get("summary").asText());
    }

    @Test
    void debtPayoffComparesBothMethods() throws Exception {
        MapleCfoFacade f = setup();
        f.addDebt("Visa", Money.of(2400), 20.99, Money.of(60));
        f.addDebt("Student loan", Money.of(900), 6.5, Money.of(40));
        JsonNode r = run("debt_payoff", "{\"extra_monthly\":200}");
        assertEquals("$200.00", r.get("extra_monthly").asText());
        String summary = r.get("summary").asText();
        assertTrue(summary.toLowerCase().contains("avalanche") && summary.toLowerCase().contains("snowball"), summary);
    }

    @Test
    void negativeExtraPaymentIsRejected() throws Exception {
        assertTrue(run("debt_payoff", "{\"extra_monthly\":-5}").has("error"));
    }

    // ------------------------------------------------------------------ fire_projection

    @Test
    void fireProjectionUsesTheProfileAge() throws Exception {
        JsonNode r = run("fire_projection", "{\"annual_spending\":40000,\"yearly_saving\":15000,\"invested\":10000}");
        assertEquals("$1,000,000.00", r.get("target_amount").asText(), "40,000 / 4%");
        assertEquals(23, r.get("current_age").asInt(), "born 2003, it is 2026");
        assertTrue(r.get("years_to_fire").asInt() > 0);
    }

    @Test
    void fireProjectionNeedsSpending() throws Exception {
        assertError(run("fire_projection", "{\"yearly_saving\":15000}"), "annual_spending");
    }

    // ------------------------------------------------------------------ contribution_room

    @Test
    void contributionRoomOkOverAndUnknown() throws Exception {
        JsonNode ok = run("contribution_room", "{\"account\":\"tfsa\",\"amount\":1000}");
        assertEquals("TFSA", ok.get("account").asText(), "lower case is accepted");
        assertEquals("OK", ok.get("status").asText());

        JsonNode over = run("contribution_room", "{\"account\":\"TFSA\",\"amount\":999999}");
        assertEquals("OVER", over.get("status").asText());
        assertTrue(over.has("excess"));

        JsonNode rrsp = run("contribution_room", "{\"account\":\"RRSP\",\"amount\":100}");
        assertEquals("UNKNOWN", rrsp.get("status").asText(), "no RRSP limit in the profile");
        assertTrue(rrsp.get("missing_profile_fields").asText().length() > 0);
    }

    @Test
    void contributionRoomRejectsOtherAccounts() throws Exception {
        assertTrue(run("contribution_room", "{\"account\":\"RESP\"}").has("error"));
        assertTrue(run("contribution_room", "{}").has("error"), "account is required");
    }

    // ------------------------------------------------------------------ budget_status / propose_action

    @Test
    void budgetStatusShowsPercentUsed() throws Exception {
        setup().setBudget(Category.DINING, Money.of(200), YearMonth.of(2026, 10));
        JsonNode r = run("budget_status", "{\"month\":\"2026-10\"}");
        JsonNode b = r.get("budgets").get(0);
        assertEquals("DINING", b.get("category").asText());
        assertEquals("$162.04", b.get("spent").asText());
        assertEquals(81, b.get("percent_used").asInt());
        assertTrue(r.get("summary").asText().contains("None are over the limit"));
    }

    @Test
    void proposeActionValidatesTheCategory() throws Exception {
        assertTrue(run("propose_action", "{\"action\":\"set_budget\",\"category\":\"PIZZA\",\"amount\":50}")
                .has("error"));
        assertTrue(run("propose_action", "{\"action\":\"delete_everything\",\"category\":\"DINING\",\"amount\":50}")
                .has("error"), "only set_budget is allowed");
    }

    // ------------------------------------------------------------------ net_worth / goal_forecast / affordability

    @Test
    void netWorthWithNoAccounts() throws Exception {
        assertTrue(run("net_worth", "{}").get("summary").asText().contains("not added any accounts"));
    }

    @Test
    void goalForecastUnknownGoal() throws Exception {
        assertEquals("No goal matches 'car'.", run("goal_forecast", "{\"goal\":\"car\"}").get("summary").asText());
    }

    @Test
    void affordabilityValidatesInput() throws Exception {
        assertError(run("affordability", "{\"amount\":500,\"date\":\"next week\"}"), "YYYY-MM-DD");
        assertError(run("affordability", "{\"amount\":500,\"date\":\"2020-01-01\"}"), "today or later");
        assertTrue(run("affordability", "{}").has("error"), "amount is required");
    }

    // ------------------------------------------------------------------ registry

    @Test
    void unknownToolsAndUnknownArgumentsAreErrors() throws Exception {
        assertError(run("delete_account", "{}"), "Unknown tool");
        assertTrue(run("spending_summary", "{\"month\":\"2026-10\",\"password\":\"x\"}").has("error"),
                "arguments outside the schema are rejected");
    }
}
