package com.maplecfo.planning;

import com.maplecfo.agent.AgentResponse;
import com.maplecfo.agent.tools.DemoRules;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.planning.AffordabilityResult.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** F11 "Can I afford X by date Y?". */
class AffordabilityTest {

    @TempDir
    Path dir;
    private MapleCfoFacade app;
    private final LocalDate today = LocalDate.of(2026, 10, 21);
    private final LocalDate in3Months = today.plusMonths(3);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Toronto"));
        app = AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")), clock);
        app.addAccount(new Account("chq", "TD Chequing", AccountType.CHEQUING, "TD", Money.of(2500)));
        app.addAccount(new Account("tfsa", "TFSA", AccountType.TFSA, "WS", Money.of(9000))); // not spendable cash
    }

    private void importSample() throws Exception {
        app.importTransactions(Path.of("sample-data/td_chequing_sample.csv"), "chequing", "TD bank account");
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> app.checkAffordability(Money.zero(), in3Months));
        assertThrows(IllegalArgumentException.class, () -> app.checkAffordability(Money.of(100), today.minusDays(1)));
    }

    @Test
    void noHistoryMeansInsufficientData() {
        AffordabilityResult r = app.checkAffordability(Money.of(500), in3Months);
        assertEquals(Verdict.INSUFFICIENT_DATA, r.verdict());
        assertTrue(r.summary().startsWith("Not enough history"));
    }

    @Test
    void projectionUsesCashSurplusAndBuffer() throws Exception {
        importSample();
        AffordabilityResult r = app.checkAffordability(Money.of(100), in3Months);
        CashFlowForecast cf = r.forecast();
        assertEquals(3, cf.monthsOfHistory());
        assertFalse(r.lowConfidence());
        assertTrue(cf.fixedCostsPerMonth().isPositive(), "sample has rent and subscriptions");
        assertEquals(Money.of(2500), r.currentCash(), "only chequing and savings count as cash");
        assertEquals(Money.of(2500).plus(cf.averageSurplus().times(3)), r.projectedCash());
        assertEquals(r.projectedCash().minus(Money.of(100)).minus(cf.fixedCostsPerMonth()), r.headroom());
    }

    @Test
    void verdictsYesTightAndNo() throws Exception {
        importSample();
        AffordabilityResult probe = app.checkAffordability(Money.of(1), in3Months);
        Money projected = probe.projectedCash();
        Money buffer = probe.safetyBuffer();
        assertTrue(projected.isPositive());

        assertEquals(Verdict.YES, app.checkAffordability(projected.minus(buffer), in3Months).verdict());

        Money tightAmount = projected.minus(buffer.dividedBy(2));
        AffordabilityResult tight = app.checkAffordability(tightAmount, in3Months);
        assertEquals(Verdict.TIGHT, tight.verdict());
        assertEquals(tightAmount.plus(buffer).minus(projected), tight.shortfall());

        AffordabilityResult no = app.checkAffordability(projected.plus(Money.of(1000)), in3Months);
        assertEquals(Verdict.NO, no.verdict());
        assertEquals(Money.of(1000).plus(buffer), no.shortfall());
    }

    @Test
    void goalContributionsReduceWhatIsAffordable() throws Exception {
        importSample();
        Money before = app.checkAffordability(Money.of(1), in3Months).projectedCash();
        app.createGoal(new SavingsGoal("car", "Car", Money.of(1200), Money.zero(), today.plusMonths(6), null));
        AffordabilityResult after = app.checkAffordability(Money.of(1), in3Months);
        assertEquals(before.minus(Money.of(600)), after.projectedCash(), "3 months x $200 for the goal");

        // an amount that only fits when the goal is paused
        Money amount = before.minus(after.safetyBuffer()).minus(Money.of(100));
        AffordabilityResult r = app.checkAffordability(amount, in3Months);
        assertNotEquals(Verdict.YES, r.verdict());
        assertEquals(java.util.List.of("Car"), r.affectedGoals());
        assertTrue(r.summary().contains("paused these goals: Car"));
    }

    @Test
    void agentUsesTheAffordabilityTool() throws Exception {
        importSample();
        AgentResponse r = app.askCfo("Can I afford a $500 laptop in 3 months?");
        assertTrue(r.toolsUsed().contains("affordability"), r.toolsUsed().toString());
        assertTrue(r.answer().contains("$500.00"), r.answer());
        assertTrue(r.grounded());
    }

    @Test
    void demoDateParsing() {
        assertEquals(Optional.of(LocalDate.of(2027, 6, 30)), DemoRules.affordDate("by june", today));
        assertEquals(Optional.of(LocalDate.of(2026, 12, 31)), DemoRules.affordDate("by december", today));
        assertEquals(Optional.of(LocalDate.of(2028, 3, 31)), DemoRules.affordDate("by march 2028", today));
        assertEquals(Optional.of(LocalDate.of(2027, 1, 21)), DemoRules.affordDate("in 3 months", today));
        assertEquals(Optional.of(LocalDate.of(2027, 2, 1)), DemoRules.affordDate("on 2027-02-01", today));
        assertEquals(Optional.empty(), DemoRules.affordDate("can i afford it", today));
    }
}
