package com.maplecfo.planning;

import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.SavingsGoal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * F11 "Can I afford X by date Y?".
 * projected cash on the date = cash today + months x (average surplus - monthly goal contributions);
 * the purchase fits when projected cash - amount still leaves one month of fixed costs as a safety buffer.
 */
public class AffordabilityAnalyzer {

    private final AccountService accounts;
    private final CashFlowForecaster cashFlow;
    private final GoalService goals;
    private final Clock clock;

    public AffordabilityAnalyzer(AccountService accounts, CashFlowForecaster cashFlow, GoalService goals, Clock clock) {
        this.accounts = accounts;
        this.cashFlow = cashFlow;
        this.goals = goals;
        this.clock = clock;
    }

    public AffordabilityResult analyze(Money amount, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("The amount must be greater than zero");
        }
        if (date == null || date.isBefore(today)) {
            throw new IllegalArgumentException("The date must be today or later");
        }
        CashFlowForecast cf = cashFlow.forecast();
        Money cash = Money.zero();
        for (Account a : accounts.getAccounts()) {
            if (a.getType() == AccountType.CHEQUING || a.getType() == AccountType.SAVINGS) {
                cash = cash.plus(a.getBalance());
            }
        }
        Money buffer = cf.fixedCostsPerMonth();
        if (!cf.hasData()) {
            return new AffordabilityResult(amount, date, AffordabilityResult.Verdict.INSUFFICIENT_DATA, cash, cash,
                    buffer, Money.zero(), Money.zero(), List.of(), cf);
        }

        long months = ChronoUnit.MONTHS.between(today, date);
        Money goalNeedsMonthly = Money.zero();
        List<String> activeGoals = new ArrayList<>();
        for (SavingsGoal g : goals.getGoals()) {
            Money need = goals.requiredMonthly(g);
            if (need.isPositive()) {
                goalNeedsMonthly = goalNeedsMonthly.plus(need);
                activeGoals.add(g.getName());
            }
        }
        Money projectedNoGoals = cash.plus(cf.cashSurplus().times(months));
        Money projected = projectedNoGoals.minus(goalNeedsMonthly.times(months));
        Money headroom = projected.minus(amount).minus(buffer);

        AffordabilityResult.Verdict verdict;
        Money shortfall;
        if (!headroom.isNegative()) {
            verdict = AffordabilityResult.Verdict.YES;
            shortfall = Money.zero();
        } else if (!projected.minus(amount).isNegative()) {
            verdict = AffordabilityResult.Verdict.TIGHT;
            shortfall = headroom.abs();
        } else {
            verdict = AffordabilityResult.Verdict.NO;
            shortfall = headroom.abs();
        }
        List<String> affected = List.of();
        if (verdict != AffordabilityResult.Verdict.YES && !activeGoals.isEmpty()
                && !projectedNoGoals.minus(amount).minus(buffer).isNegative()) {
            affected = activeGoals;
        }
        return new AffordabilityResult(amount, date, verdict, cash, projected, buffer, headroom, shortfall,
                affected, cf);
    }
}
