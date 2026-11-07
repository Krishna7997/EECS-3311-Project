package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.util.List;

/**
 * Result of a FIRE projection (F07).
 * When the target is not reached within {@link FireCalculator#MAX_YEARS},
 * {@code yearsToFire} and {@code fireAge} are -1 and {@link #reachable()} is false.
 *
 * @param yearlyBalances      balance at the start (index 0) and after each simulated year
 * @param scenarios           the same projection with 5% of income saved less or more, and returns 1 point lower or higher
 * @param safeYearlySpending  what the target lets you spend each year at the withdrawal rate
 * @param coastAge            age used for the Coast FIRE number (your target age, else 65)
 * @param coastNumber         invested today that would grow to the target by the coast age with no more saving
 * @param savingNeeded        yearly saving needed to reach the target by your target age, or null without one
 */
public record FireProjection(Money targetAmount, int yearsToFire, int fireAge,
                             List<Money> yearlyBalances, List<Scenario> scenarios,
                             Money safeYearlySpending, int coastAge, Money coastNumber, Money savingNeeded) {

    public FireProjection {
        yearlyBalances = List.copyOf(yearlyBalances);
        scenarios = List.copyOf(scenarios);
    }

    /** Already invested enough that growth alone reaches the target by the coast age. */
    public boolean coastReached() {
        return coastNumber != null && !yearlyBalances.get(0).isLessThan(coastNumber);
    }

    public boolean reachable() {
        return yearsToFire >= 0;
    }

    /** One what-if row in the scenario table. */
    public record Scenario(String label, Money yearlySaving, Money annualSpending,
                           Money targetAmount, int yearsToFire) {
        public boolean reachable() {
            return yearsToFire >= 0;
        }
    }
}
