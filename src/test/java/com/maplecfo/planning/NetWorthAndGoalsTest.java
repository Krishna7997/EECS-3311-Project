package com.maplecfo.planning;

import com.maplecfo.agent.AgentResponse;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** F03 net worth and F09 savings goals, through the facade (same wiring as the real app). */
class NetWorthAndGoalsTest {

    @TempDir
    Path dir;
    private MapleCfoFacade app;
    private final Clock oct2026 = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Toronto"));

    @BeforeEach
    void setUp() {
        app = AppContext.create(DatabaseManager.forFile(dir.resolve("t.db")), oct2026);
    }

    private void addThreeAccounts() {
        app.addAccount(new Account("chq", "TD Chequing", AccountType.CHEQUING, "TD", Money.of(2500)));
        app.addAccount(new Account("tfsa", "Wealthsimple TFSA", AccountType.TFSA, "WS", Money.of(6000)));
        app.addAccount(new Account("visa", "RBC Visa", AccountType.CREDIT_CARD, "RBC", Money.of(800)));
    }

    // ------------------------------------------------------------------ F03

    @Test
    void emptyStateHasZeroNetWorth() {
        assertTrue(app.getAccounts().isEmpty());
        assertEquals(Money.zero(), app.getNetWorth().total());
        assertTrue(app.getNetWorthHistory().isEmpty());
    }

    @Test
    void netWorthAddsAssetsAndSubtractsDebtsByGroup() {
        addThreeAccounts();
        NetWorthSnapshot s = app.getNetWorth();
        assertEquals(Money.of(7700), s.total());
        assertEquals(Money.of(8500), s.assets());
        assertEquals(Money.of(800), s.liabilities());
        assertEquals(List.of("Cash", "Registered", "Debts"), List.copyOf(s.groups().keySet()));
        assertEquals(Money.of(-800), s.groups().get("Debts"));
        // one history point per day, replaced by later saves on the same day
        assertEquals(1, app.getNetWorthHistory().size());
        assertEquals(Money.of(7700), app.getNetWorthHistory().get(LocalDate.of(2026, 10, 21)));
    }

    @Test
    void updatingABalanceUpdatesNetWorthAndHistory() {
        addThreeAccounts();
        app.updateAccountBalance("visa", Money.of(300));
        assertEquals(Money.of(8200), app.getNetWorth().total());
        assertEquals(Money.of(8200), app.getNetWorthHistory().get(LocalDate.of(2026, 10, 21)));
        app.removeAccount("tfsa");
        assertEquals(Money.of(2200), app.getNetWorth().total());
    }

    @Test
    void accountValidation() {
        addThreeAccounts();
        List<String> w = app.addAccount(new Account("chq2", "td chequing", AccountType.CHEQUING, "TD", Money.of(1)));
        assertEquals(1, w.size(), "duplicate name gives a warning, not an error");
        assertThrows(IllegalArgumentException.class,
                () -> app.addAccount(new Account("chq", "Other", AccountType.SAVINGS, "TD", Money.of(1))));
        assertThrows(IllegalArgumentException.class,
                () -> app.addAccount(new Account("neg", "Neg", AccountType.SAVINGS, "TD", Money.of(-5))));
    }

    @Test
    void agentAnswersNetWorthFromTheTool() {
        addThreeAccounts();
        AgentResponse r = app.askCfo("What is my net worth?");
        assertTrue(r.toolsUsed().contains("net_worth"), r.toolsUsed().toString());
        assertTrue(r.answer().contains("$7,700.00"), r.answer());
        assertTrue(r.grounded());
    }

    // ------------------------------------------------------------------ F09

    @Test
    void goalValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new SavingsGoal("g", "Trip", Money.zero(), Money.zero(), LocalDate.of(2027, 6, 1), null));
        assertThrows(IllegalArgumentException.class, () -> app.createGoal(
                new SavingsGoal("g", "Trip", Money.of(1000), Money.zero(), LocalDate.of(2026, 10, 1), null)));
        assertTrue(app.getGoals().isEmpty());
    }

    @Test
    void createGoalIsUndoable() {
        app.createGoal(new SavingsGoal("trip", "Trip", Money.of(1200), Money.zero(), LocalDate.of(2027, 4, 21), null));
        assertEquals(1, app.getGoals().size());
        assertTrue(app.describeUndo().contains("Create goal Trip"));
        assertTrue(app.undoLastEdit());
        assertTrue(app.getGoals().isEmpty());
    }

    @Test
    void withoutHistoryTheForecastIsLowConfidenceAndNotOnTrack() {
        app.createGoal(new SavingsGoal("trip", "Trip", Money.of(1200), Money.of(200), LocalDate.of(2027, 4, 21), null));
        GoalForecast f = app.forecastGoal("trip");
        assertEquals(6, f.monthsLeft());
        assertEquals(Money.of("166.67"), f.requiredMonthly());
        assertTrue(f.lowConfidence());
        assertFalse(f.onTrack());
        assertEquals(Money.of("166.67"), f.shortfallMonthly());
        assertNull(f.projectedCompletion());
    }

    @Test
    void forecastUsesTheAverageSurplusFromImportedHistory() throws Exception {
        app.importTransactions(Path.of("sample-data/td_chequing_sample.csv"), "chequing", "TD bank account");
        app.createGoal(new SavingsGoal("ef", "Emergency fund", Money.of(1000), Money.zero(),
                LocalDate.of(2027, 10, 21), null));
        GoalForecast f = app.forecastGoal("ef");
        assertFalse(f.lowConfidence(), "Jul, Aug and Sep 2026 are in the sample");
        assertEquals(12, f.monthsLeft());
        assertEquals(Money.of("83.33"), f.requiredMonthly());
        assertEquals(!f.availableMonthly().isLessThan(f.requiredMonthly()), f.onTrack());
        if (f.availableMonthly().isPositive()) {
            assertNotNull(f.projectedCompletion());
        }
    }

    @Test
    void otherGoalsReduceTheMoneyAvailable() throws Exception {
        app.importTransactions(Path.of("sample-data/td_chequing_sample.csv"), "chequing", "TD bank account");
        app.createGoal(new SavingsGoal("a", "A", Money.of(600), Money.zero(), LocalDate.of(2027, 4, 21), null));
        Money aloneAvailable = app.forecastGoal("a").availableMonthly();
        app.createGoal(new SavingsGoal("b", "B", Money.of(600), Money.zero(), LocalDate.of(2027, 4, 21), null));
        Money withB = app.forecastGoal("a").availableMonthly();
        assertEquals(aloneAvailable.minus(Money.of(100)).max(Money.zero()), withB);
    }

    @Test
    void reachingAGoalRaisesAnAlert() {
        app.createGoal(new SavingsGoal("trip", "Trip", Money.of(500), Money.of(400), LocalDate.of(2027, 4, 21), null));
        assertTrue(app.getAlerts().isEmpty());
        SavingsGoal g = app.recordGoalDeposit("trip", Money.of(100));
        assertTrue(g.isComplete());
        assertTrue(app.forecastGoal("trip").onTrack());
        assertTrue(app.getAlerts().stream().anyMatch(a -> a.message().startsWith("Goal reached: Trip")));
    }

    @Test
    void goalsSurviveARestart() {
        app.createGoal(new SavingsGoal("trip", "Trip", Money.of(500), Money.of(50), LocalDate.of(2027, 4, 21), "chq"));
        app.recordGoalDeposit("trip", Money.of(25));
        MapleCfoFacade reopened = AppContext.create(DatabaseManager.forFile(dir.resolve("t.db")), oct2026);
        SavingsGoal g = reopened.getGoals().get(0);
        assertEquals(Money.of(75), g.getSaved());
        assertEquals(LocalDate.of(2027, 4, 21), g.getDeadline());
        assertEquals("chq", g.getLinkedAccountId());
    }

    @Test
    void agentAnswersGoalQuestionsFromTheTool() {
        app.createGoal(new SavingsGoal("trip", "Trip", Money.of(1200), Money.of(200), LocalDate.of(2027, 4, 21), null));
        AgentResponse r = app.askCfo("Am I on track for my goal?");
        assertTrue(r.toolsUsed().contains("goal_forecast"), r.toolsUsed().toString());
        assertTrue(r.answer().contains("Not on track"), r.answer());
    }
}
