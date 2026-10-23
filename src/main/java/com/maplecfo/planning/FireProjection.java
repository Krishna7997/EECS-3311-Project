package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.util.List;

/**
 * Result of a FIRE projection (F07).
 * When the target is not reached within {@link FireCalculator#MAX_YEARS},
 * {@code yearsToFire} and {@code fireAge} are -1 and {@link #reachable()} is false.
 *
 * @param yearlyBalances balance at the start (index 0) and after each simulated year
 * @param scenarios      the same projection at savings rates 5 points lower and higher
 */
public record FireProjection(Money targetAmount, int yearsToFire, int fireAge,
                             List<Money> yearlyBalances, List<Scenario> scenarios) {

    public FireProjection {
        yearlyBalances = List.copyOf(yearlyBalances);
        scenarios = List.copyOf(scenarios);
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
