package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * F07 FIRE projection. A pure calculator with no dependencies, so every result
 * can be unit-tested with exact expected values.
 *
 * <p>target = annual spending / withdrawal rate (or your own target). Each simulated year:
 * balance = balance x (1 + real return) + yearly saving, until the balance
 * reaches the target or {@link #MAX_YEARS} years pass.
 */
public class FireCalculator {

    public static final int MAX_YEARS = 70;
    /** Usual retirement age, used for the Coast FIRE number when no target age is given. */
    public static final int DEFAULT_COAST_AGE = 65;
    /** Return scenarios: one percentage point lower and higher. */
    static final double RETURN_STEP = 0.01;
    /** Scenario step: savings rate 5 percentage points lower and higher. */
    static final double SCENARIO_STEP = 0.05;

    public FireProjection project(FireInputs in) {
        Money target = targetFor(in);
        List<Money> balances = simulate(in.invested(), in.yearlySaving(), in.realReturn(), target);
        int years = balances.get(balances.size() - 1).isLessThan(target) ? -1 : balances.size() - 1;
        int fireAge = years < 0 ? -1 : in.currentAge() + years;
        int coastAge = in.targetAge() != null ? in.targetAge() : Math.max(DEFAULT_COAST_AGE, in.currentAge() + 1);
        Money coast = target.dividedBy(Math.pow(1 + in.realReturn(), coastAge - in.currentAge()));
        Money needed = in.targetAge() == null ? null
                : savingNeeded(in.invested(), target, in.realReturn(), in.targetAge() - in.currentAge());
        return new FireProjection(target, years, fireAge, balances, scenarios(in),
                target.times(in.withdrawalRate()), coastAge, coast, needed);
    }

    /**
     * Yearly saving that grows {@code invested} to {@code target} in {@code years} years (end-of-year deposits):
     * saving = (target - invested x (1+r)^n) x r / ((1+r)^n - 1), or the shortfall / n when r is 0.
     * Zero when the investments already get there on their own.
     */
    static Money savingNeeded(Money invested, Money target, double r, int years) {
        double growth = Math.pow(1 + r, years);
        Money gap = target.minus(invested.times(growth));
        if (!gap.isPositive()) {
            return Money.zero();
        }
        return Math.abs(r) < 1e-9 ? gap.dividedBy(years) : gap.times(r / (growth - 1));
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

    /** 0.05 -> "5%", 0.045 -> "4.5%". */
    public static String percent(double rate) {
        double p = Math.round(rate * 1000) / 10.0;
        return (p == Math.rint(p) ? String.valueOf((long) p) : String.valueOf(p)) + "%";
    }

    /** Your own target if you set one, otherwise spending / withdrawal rate. */
    static Money targetFor(FireInputs in) {
        return in.hasCustomTarget() ? in.targetOverride() : targetFor(in.annualSpending(), in.withdrawalRate());
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
        // how much the answer depends on the markets
        for (double d : new double[] {-RETURN_STEP, RETURN_STEP}) {
            double r = in.realReturn() + d;
            if (r < FireInputs.MIN_RETURN || r > FireInputs.MAX_RETURN) continue;
            FireInputs alt = in.withReturn(r);
            Money target = targetFor(alt);
            result.add(new FireProjection.Scenario("Returns " + percent(r) + " instead of " + percent(in.realReturn()), in.yearlySaving(), in.annualSpending(), target,
                    yearsToFire(in.invested(), in.yearlySaving(), r, target)));
        }
        return result;
    }

    private void addScenario(List<FireProjection.Scenario> out, FireInputs base, String label,
                             Money saving, Money spending) {
        if (saving.isNegative() || !spending.isPositive()) {
            return; // the scenario does not make sense for these inputs
        }
        FireInputs in = base.withSavingAndSpending(saving, spending);
        Money target = targetFor(in);
        int years = yearsToFire(in.invested(), in.yearlySaving(), in.realReturn(), target);
        out.add(new FireProjection.Scenario(label, saving, spending, target, years));
    }
}
