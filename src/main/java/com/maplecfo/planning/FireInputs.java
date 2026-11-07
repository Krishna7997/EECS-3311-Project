package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.util.Objects;

/**
 * Inputs for a FIRE projection (F07). Rates are fractions: 0.05 means 5%.
 * The constructor enforces the Stage 1 validation rules.
 *
 * @param targetOverride your own target amount instead of spending / withdrawal rate, or null
 * @param targetAge      the age you would like to retire at, or null (used for "how much do I need to save")
 */
public record FireInputs(Money invested, Money yearlySaving, Money annualSpending,
                         double realReturn, double withdrawalRate, int currentAge,
                         Money targetOverride, Integer targetAge) {

    public static final double MIN_RETURN = -0.05;
    public static final double MAX_RETURN = 0.15;
    public static final double MIN_WITHDRAWAL = 0.02;
    public static final double MAX_WITHDRAWAL = 0.06;

    public FireInputs {
        Objects.requireNonNull(invested, "invested");
        Objects.requireNonNull(yearlySaving, "yearlySaving");
        Objects.requireNonNull(annualSpending, "annualSpending");
        if (invested.isNegative() || yearlySaving.isNegative()) {
            throw new IllegalArgumentException("Investments and yearly saving cannot be negative");
        }
        if (!annualSpending.isPositive()) {
            throw new IllegalArgumentException("Annual spending must be greater than zero");
        }
        if (realReturn < MIN_RETURN || realReturn > MAX_RETURN) {
            throw new IllegalArgumentException("Real return must be between -5% and 15%");
        }
        if (withdrawalRate < MIN_WITHDRAWAL || withdrawalRate > MAX_WITHDRAWAL) {
            throw new IllegalArgumentException("Withdrawal rate must be between 2% and 6%");
        }
        if (currentAge < 0 || currentAge > 120) {
            throw new IllegalArgumentException("Age must be between 0 and 120");
        }
        if (targetOverride != null && !targetOverride.isPositive()) {
            throw new IllegalArgumentException("Your own target must be greater than zero");
        }
        if (targetAge != null && (targetAge <= currentAge || targetAge > 100)) {
            throw new IllegalArgumentException("The age you want to retire at must be after your current age and at most 100");
        }
    }

    /** The Stage 1 inputs: target = spending / withdrawal rate, no target age. */
    public FireInputs(Money invested, Money yearlySaving, Money annualSpending, double realReturn,
                      double withdrawalRate, int currentAge) {
        this(invested, yearlySaving, annualSpending, realReturn, withdrawalRate, currentAge, null, null);
    }

    public boolean hasCustomTarget() {
        return targetOverride != null;
    }

    /** Copy with a different saving and spending split (used for the scenarios). */
    FireInputs withSavingAndSpending(Money saving, Money spending) {
        return new FireInputs(invested, saving, spending, realReturn, withdrawalRate, currentAge, targetOverride, targetAge);
    }

    /** Copy with a different expected return (used for the scenarios). */
    FireInputs withReturn(double r) {
        return new FireInputs(invested, yearlySaving, annualSpending, r, withdrawalRate, currentAge, targetOverride, targetAge);
    }
}
