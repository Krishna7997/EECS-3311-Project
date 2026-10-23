package com.maplecfo.planning;

import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.memory.InMemoryDebtRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebtPayoffPlannerTest {

    private InMemoryDebtRepository repo;
    private DebtPayoffPlanner planner;
    private final Clock oct2026 = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("America/Toronto"));

    @BeforeEach
    void setUp() {
        repo = new InMemoryDebtRepository();
        planner = new DebtPayoffPlanner(repo, new AvalancheStrategy(), oct2026);
    }

    private Debt debt(String id, long balance, double apr, long min) {
        return new Debt(id, id, Money.of(balance), apr, Money.of(min), null);
    }

    @Test
    void noDebtsMeansDebtFree() {
        PayoffPlan plan = planner.plan(Money.of(100));
        assertTrue(plan.isDebtFreeAlready());
        assertEquals(Money.zero(), plan.totalInterest());
        assertFalse(plan.warnings().isEmpty());
    }

    @Test
    void zeroInterestDebtIsSimpleDivision() {
        repo.save(debt("Phone", 1200, 0.0, 100));
        PayoffPlan plan = planner.plan(Money.zero());
        assertEquals(12, plan.monthsToDebtFree());
        assertEquals(Money.zero(), plan.totalInterest());
        assertEquals(Money.of(1200), plan.totalPaid());
        assertEquals(YearMonth.of(2027, 10), plan.debtFreeMonth());
    }

    @Test
    void firstMonthInterestIsCharged() {
        repo.save(debt("Card", 1200, 24.0, 100));
        PayoffPlan plan = planner.plan(Money.zero());
        assertEquals(Money.of(24), plan.schedule().get(0).interest());
        assertEquals(Money.of(1124), plan.schedule().get(0).remainingBalance());
    }

    @Test
    void strategiesOrderDebtsDifferently() {
        Debt card = debt("Card", 3000, 20.0, 60);
        Debt loan = debt("Loan", 800, 6.0, 50);
        assertEquals(List.of(card, loan), new AvalancheStrategy().orderDebts(List.of(loan, card)));
        assertEquals(List.of(loan, card), new SnowballStrategy().orderDebts(List.of(card, loan)));
    }

    @Test
    void avalancheNeverCostsMoreInterestThanSnowball() {
        repo.save(debt("Card", 3000, 20.0, 60));
        repo.save(debt("Loan", 800, 6.0, 50));
        PayoffPlan avalanche = planner.plan(Money.of(200));
        planner.setStrategy(new SnowballStrategy());
        PayoffPlan snowball = planner.plan(Money.of(200));

        assertEquals("Avalanche", avalanche.strategyName());
        assertEquals("Snowball", snowball.strategyName());
        assertTrue(avalanche.completes() && snowball.completes());
        assertFalse(avalanche.totalInterest().isGreaterThan(snowball.totalInterest()));
        assertEquals("Loan", snowball.payoffs().get(0).debtName(), "snowball clears the small loan first");
    }

    @Test
    void extraPaymentShortensThePlan() {
        repo.save(debt("Card", 3000, 20.0, 60));
        int without = planner.plan(Money.zero()).monthsToDebtFree();
        int with = planner.plan(Money.of(200)).monthsToDebtFree();
        assertTrue(with < without, with + " < " + without);
    }

    @Test
    void warnsWhenMinimumDoesNotCoverInterest() {
        repo.save(debt("Card", 10_000, 24.0, 150)); // interest is $200/month
        PayoffPlan plan = planner.plan(Money.zero());
        assertFalse(plan.completes());
        assertTrue(plan.warnings().get(0).contains("Card"));
    }

    @Test
    void realDebtObjectsAreNotModified() {
        Debt card = debt("Card", 500, 10.0, 100);
        repo.save(card);
        planner.plan(Money.of(50));
        assertEquals(Money.of(500), card.getBalance());
    }

    @Test
    void negativeExtraIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> planner.plan(Money.of(-1)));
    }
}
