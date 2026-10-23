package com.maplecfo.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void roundsToCentsWithoutFloatingPointErrors() {
        Money a = Money.of("0.10");
        Money b = Money.of("0.20");
        assertEquals(Money.of("0.30"), a.plus(b));
        assertEquals(new BigDecimal("0.30"), a.plus(b).getAmount());
    }

    @Test
    void arithmeticReturnsNewObjects() {
        Money base = Money.of(100);
        Money result = base.minus(Money.of("25.50"));
        assertEquals(Money.of("74.50"), result);
        assertEquals(Money.of(100), base, "Money must be immutable");
    }

    @Test
    void timesAndDividedBy() {
        assertEquals(Money.of(1000), Money.of(40000).times(0.025));
        assertEquals(Money.of(1000000), Money.of(40000).dividedBy(0.04));
    }

    @Test
    void divisionByZeroIsRejected() {
        assertThrows(ArithmeticException.class, () -> Money.of(10).dividedBy(0));
    }

    @Test
    void signChecks() {
        assertTrue(Money.of(-5).isNegative());
        assertTrue(Money.of(5).isPositive());
        assertTrue(Money.zero().isZero());
        assertEquals(Money.of(5), Money.of(-5).abs());
        assertEquals(Money.of(-5), Money.of(5).negate());
    }

    @Test
    void equalityIgnoresTrailingZeros() {
        assertEquals(Money.of("12.5"), Money.of("12.50"));
        assertEquals(Money.of("12.5").hashCode(), Money.of("12.50").hashCode());
    }

    @Test
    void comparisonsAndMinMax() {
        Money small = Money.of(10);
        Money big = Money.of(20);
        assertTrue(big.isGreaterThan(small));
        assertTrue(small.isLessThan(big));
        assertEquals(small, small.min(big));
        assertEquals(big, small.max(big));
    }

    @Test
    void formatsForDisplay() {
        assertEquals("$1,234.50", Money.of("1234.5").toString());
        assertEquals("-$20.00", Money.of(-20).toString());
    }
}
