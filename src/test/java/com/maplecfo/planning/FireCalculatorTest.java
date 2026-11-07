package com.maplecfo.planning;

import com.maplecfo.domain.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FireCalculatorTest {

    private final FireCalculator calc = new FireCalculator();

    private FireInputs inputs(long invested, long saving, long spending, double ret, double wr, int age) {
        return new FireInputs(Money.of(invested), Money.of(saving), Money.of(spending), ret, wr, age);
    }

    @Test
    void targetIsSpendingDividedByWithdrawalRate() {
        FireProjection p = calc.project(inputs(0, 10_000, 40_000, 0.05, 0.04, 25));
        assertEquals(Money.of(1_000_000), p.targetAmount());
    }

    @Test
    void zeroReturnIsSimpleSaving() {
        // 100,000 needed (4,000 / 4%), 10,000 per year at 0% return -> exactly 10 years
        FireProjection p = calc.project(inputs(0, 10_000, 4_000, 0.0, 0.04, 30));
        assertEquals(10, p.yearsToFire());
        assertEquals(40, p.fireAge());
        assertEquals(11, p.yearlyBalances().size(), "year 0 plus 10 simulated years");
        assertEquals(Money.of(100_000), p.yearlyBalances().get(10));
    }

    @Test
    void compoundGrowthMatchesHandCalculation() {
        // 1000 at 10% with 0 saving: 1000, 1100, 1210 -> reaches 1210 target in 2 years
        int years = calc.yearsToFire(Money.of(1000), Money.zero(), 0.10, Money.of(1210));
        assertEquals(2, years);
    }

    @Test
    void alreadyAtTargetMeansZeroYears() {
        FireProjection p = calc.project(inputs(2_000_000, 0, 40_000, 0.03, 0.04, 50));
        assertEquals(0, p.yearsToFire());
        assertEquals(50, p.fireAge());
    }

    @Test
    void unreachableWithinSeventyYears() {
        FireProjection p = calc.project(inputs(0, 100, 60_000, 0.0, 0.02, 20));
        assertFalse(p.reachable());
        assertEquals(-1, p.yearsToFire());
        assertEquals(-1, p.fireAge());
        assertEquals(FireCalculator.MAX_YEARS + 1, p.yearlyBalances().size());
    }

    @Test
    void savingMoreReachesFireSooner() {
        FireProjection p = calc.project(inputs(10_000, 15_000, 40_000, 0.05, 0.04, 25));
        assertEquals(5, p.scenarios().size(), "3 saving scenarios + returns 1 point lower and higher");
        int less = p.scenarios().get(0).yearsToFire();
        int current = p.scenarios().get(1).yearsToFire();
        int more = p.scenarios().get(2).yearsToFire();
        assertEquals(p.yearsToFire(), current);
        assertTrue(less > current && current > more, less + " > " + current + " > " + more);
    }

    @Test
    void validationRules() {
        assertThrows(IllegalArgumentException.class, () -> inputs(-1, 0, 40_000, 0.05, 0.04, 25));
        assertThrows(IllegalArgumentException.class, () -> inputs(0, 0, 40_000, 0.20, 0.04, 25));
        assertThrows(IllegalArgumentException.class, () -> inputs(0, 0, 40_000, 0.05, 0.10, 25));
        assertThrows(IllegalArgumentException.class, () -> inputs(0, 0, 0, 0.05, 0.04, 25));
        assertDoesNotThrow(() -> inputs(0, 0, 40_000, -0.05, 0.02, 25));
    }

    // ---------------------------------------------------------------- your own target, target age, coast FIRE

    private FireInputs custom(long invested, long saving, long spending, double ret, int age, Long target, Integer at) {
        return new FireInputs(Money.of(invested), Money.of(saving), Money.of(spending), ret, 0.04, age,
                target == null ? null : Money.of(target), at);
    }

    @Test
    void ownTargetReplacesTheRuleAndShowsWhatItPaysFor() {
        FireProjection p = calc.project(custom(0, 10_000, 40_000, 0.0, 30, 500_000L, null));
        assertEquals(Money.of(500_000), p.targetAmount());
        assertEquals(50, p.yearsToFire(), "500,000 / 10,000 at 0%");
        assertEquals(Money.of(20_000), p.safeYearlySpending(), "4% of the target");
        FireProjection.Scenario less = p.scenarios().get(0);
        assertEquals(Money.of(500_000), less.targetAmount(), "spending changes do not move your own target");
    }

    @Test
    void savingNeededForATargetAge() {
        // 0% return: (1,000,000 - 100,000) / 30 years = 30,000 a year
        FireProjection p = calc.project(custom(100_000, 10_000, 40_000, 0.0, 30, null, 60));
        assertEquals(Money.of(30_000), p.savingNeeded());
        // 5% over 10 years, nothing invested: 1,000,000 x 0.05 / (1.05^10 - 1) = 79,504.57
        FireProjection q = calc.project(custom(0, 10_000, 40_000, 0.05, 30, null, 40));
        assertEquals(Money.of("79504.57"), q.savingNeeded());
        assertNull(calc.project(inputs(0, 10_000, 40_000, 0.05, 0.04, 30)).savingNeeded());
        // already enough: no saving needed
        assertEquals(Money.zero(), calc.project(custom(2_000_000, 0, 40_000, 0.05, 30, null, 40)).savingNeeded());
    }

    @Test
    void coastNumberAndReturnScenarios() {
        FireProjection p = calc.project(custom(100_000, 10_000, 40_000, 0.05, 25, null, null));
        assertEquals(65, p.coastAge());
        assertEquals(Money.of(1_000_000).dividedBy(Math.pow(1.05, 40)), p.coastNumber());
        assertFalse(p.coastReached());
        assertTrue(calc.project(custom(200_000, 0, 40_000, 0.05, 25, null, null)).coastReached());
        assertEquals("Returns 4% instead of 5%", p.scenarios().get(3).label());
        assertTrue(p.scenarios().get(3).yearsToFire() > p.scenarios().get(4).yearsToFire(), "lower returns take longer");
        assertEquals("4.5%", FireCalculator.percent(0.045));
    }

    @Test
    void invalidTargetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> custom(0, 10_000, 40_000, 0.05, 30, 0L, null));
        assertThrows(IllegalArgumentException.class, () -> custom(0, 10_000, 40_000, 0.05, 30, null, 30));
    }
}
