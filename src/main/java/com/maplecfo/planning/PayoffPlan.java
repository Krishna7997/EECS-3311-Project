package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.time.YearMonth;
import java.util.List;

/**
 * Result of a debt payoff simulation (F08).
 *
 * @param monthsToDebtFree -1 when the debts are not paid off within the simulation limit
 * @param schedule         one row per simulated month
 * @param payoffs          when each debt is paid off, in payoff order
 * @param warnings         e.g. debts whose minimum payment does not cover the interest
 */
public record PayoffPlan(String strategyName, int monthsToDebtFree, YearMonth debtFreeMonth,
                         Money totalInterest, Money totalPaid, List<MonthRow> schedule,
                         List<DebtPayoff> payoffs, List<String> warnings) {

    public PayoffPlan {
        schedule = List.copyOf(schedule);
        payoffs = List.copyOf(payoffs);
        warnings = List.copyOf(warnings);
    }

    public boolean isDebtFreeAlready() {
        return monthsToDebtFree == 0;
    }

    public boolean completes() {
        return monthsToDebtFree >= 0;
    }

    /** Totals for one month of the schedule (month 1 is the first payment). */
    public record MonthRow(int month, Money interest, Money payment, Money remainingBalance) {
    }

    /** The month a single debt reaches zero. */
    public record DebtPayoff(String debtName, int month, YearMonth payoffMonth) {
    }
}
