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
        assertEquals(3, p.scenarios().size());
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
}
