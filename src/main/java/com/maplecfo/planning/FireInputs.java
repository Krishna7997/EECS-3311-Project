package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.util.Objects;

/**
 * Inputs for a FIRE projection (F07). Rates are fractions: 0.05 means 5%.
 * The constructor enforces the Stage 1 validation rules.
 */
public record FireInputs(Money invested, Money yearlySaving, Money annualSpending,
                         double realReturn, double withdrawalRate, int currentAge) {

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
    }

    /** Copy with a different saving and spending split (used for the scenarios). */
    FireInputs withSavingAndSpending(Money saving, Money spending) {
        return new FireInputs(invested, saving, spending, realReturn, withdrawalRate, currentAge);
    }
}
