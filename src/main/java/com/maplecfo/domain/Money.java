package com.maplecfo.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable money value object (Stage 1 design decision D11).
 * Amounts are stored as BigDecimal rounded to cents, so totals never suffer
 * from floating-point rounding errors. All arithmetic returns a new Money.
 */
public final class Money implements Comparable<Money> {

    public static final String DEFAULT_CURRENCY = "CAD";
    private static final int SCALE = 2;

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = Objects.requireNonNull(amount, "amount").setScale(SCALE, RoundingMode.HALF_EVEN);
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount.trim()), DEFAULT_CURRENCY);
    }

    public static Money of(long dollars) {
        return new Money(BigDecimal.valueOf(dollars), DEFAULT_CURRENCY);
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount), DEFAULT_CURRENCY);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO, DEFAULT_CURRENCY);
    }

    public Money plus(Money m) {
        requireSameCurrency(m);
        return new Money(amount.add(m.amount), currency);
    }

    public Money minus(Money m) {
        requireSameCurrency(m);
        return new Money(amount.subtract(m.amount), currency);
    }

    public Money times(double factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    /** Divides by a positive factor, for example annual spending / withdrawal rate. */
    public Money dividedBy(double divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("division by zero");
        }
        return new Money(amount.divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_EVEN), currency);
    }

    public Money negate() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public Money max(Money other) {
        return compareTo(other) >= 0 ? this : other;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;
        return amount.equals(m.amount) && currency.equals(m.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    /** Formats like "$1,234.50" or "-$20.00". */
    @Override
    public String toString() {
        String digits = String.format(Locale.CANADA, "%,.2f", amount.abs());
        return (isNegative() ? "-$" : "$") + digits;
    }
}
