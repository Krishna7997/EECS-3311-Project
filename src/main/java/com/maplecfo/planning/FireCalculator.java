package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * F07 FIRE projection. A pure calculator with no dependencies, so every result
 * can be unit-tested with exact expected values.
 *
 * <p>target = annual spending / withdrawal rate. Each simulated year:
 * balance = balance x (1 + real return) + yearly saving, until the balance
 * reaches the target or {@link #MAX_YEARS} years pass.
 */
public class FireCalculator {

    public static final int MAX_YEARS = 70;
    /** Scenario step: savings rate 5 percentage points lower and higher. */
    static final double SCENARIO_STEP = 0.05;

    public FireProjection project(FireInputs in) {
        Money target = targetFor(in.annualSpending(), in.withdrawalRate());
        List<Money> balances = simulate(in.invested(), in.yearlySaving(), in.realReturn(), target);
        int years = balances.get(balances.size() - 1).isLessThan(target) ? -1 : balances.size() - 1;
        int fireAge = years < 0 ? -1 : in.currentAge() + years;
        return new FireProjection(target, years, fireAge, balances, scenarios(in));
    }

    /**
     * Years until {@code invested} grows to {@code target}, or -1 when it does not
     * get there within {@link #MAX_YEARS} years.
     */
    public int yearsToFire(Money invested, Money yearlySaving, double rate, Money target) {
        List<Money> balances = simulate(invested, yearlySaving, rate, target);
        return balances.get(balances.size() - 1).isLessThan(target) ? -1 : balances.size() - 1;
    }

    private List<Money> simulate(Money invested, Money yearlySaving, double rate, Money target) {
        List<Money> balances = new ArrayList<>();
        Money balance = invested;
        balances.add(balance);
        for (int year = 1; year <= MAX_YEARS && balance.isLessThan(target); year++) {
            balance = balance.times(1 + rate).plus(yearlySaving);
            balances.add(balance);
        }
        return balances;
    }

    static Money targetFor(Money annualSpending, double withdrawalRate) {
        return annualSpending.dividedBy(withdrawalRate);
    }

    /**
     * Income is held constant (saving + spending). The scenarios move 5% of
     * income from spending to saving, or the other way round. A lower spending
     * also lowers the target, which is why saving more helps twice.
     */
    private List<FireProjection.Scenario> scenarios(FireInputs in) {
        Money income = in.yearlySaving().plus(in.annualSpending());
        Money step = income.times(SCENARIO_STEP);
        List<FireProjection.Scenario> result = new ArrayList<>();
        addScenario(result, in, "Save 5% less", in.yearlySaving().minus(step), in.annualSpending().plus(step));
        addScenario(result, in, "Current plan", in.yearlySaving(), in.annualSpending());
        addScenario(result, in, "Save 5% more", in.yearlySaving().plus(step), in.annualSpending().minus(step));
        return result;
    }

    private void addScenario(List<FireProjection.Scenario> out, FireInputs base, String label,
                             Money saving, Money spending) {
        if (saving.isNegative() || !spending.isPositive()) {
            return; // the scenario does not make sense for these inputs
        }
        FireInputs in = base.withSavingAndSpending(saving, spending);
        Money target = targetFor(in.annualSpending(), in.withdrawalRate());
        int years = yearsToFire(in.invested(), in.yearlySaving(), in.realReturn(), target);
        out.add(new FireProjection.Scenario(label, saving, spending, target, years));
    }
}
