package com.maplecfo.planning;

import com.maplecfo.domain.Frequency;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecurringChargeDetectorTest {

    private final RecurringChargeDetector detector = new RecurringChargeDetector();
    private int ids = 0;

    private Transaction tx(String date, String merchant, String amount) {
        return new Transaction("t" + (ids++), LocalDate.parse(date), merchant, merchant, Money.of(amount), "acc");
    }

    @Test
    void findsMonthlySubscriptionAndPredictsNextDate() {
        List<Transaction> txs = List.of(
                tx("2026-06-03", "NETFLIX.COM 866-579", "-16.99"),
                tx("2026-07-03", "Netflix.com", "-16.99"),
                tx("2026-08-04", "NETFLIX.COM", "-16.99"),
                tx("2026-09-03", "Netflix.com", "-16.99"));
        List<RecurringCharge> found = detector.detect(txs);
        assertEquals(1, found.size());
        RecurringCharge netflix = found.get(0);
        assertEquals(Frequency.MONTHLY, netflix.frequency());
        assertEquals(Money.of("16.99"), netflix.averageAmount());
        assertEquals(LocalDate.of(2026, 10, 3), netflix.nextExpectedDate());
        assertEquals(Money.of("203.88"), netflix.yearlyCost());
        assertFalse(netflix.priceIncreased());
        assertFalse(netflix.variableAmount());
    }

    @Test
    void flagsPriceIncreaseAboveFivePercent() {
        List<Transaction> txs = List.of(
                tx("2026-06-10", "Spotify", "-11.99"),
                tx("2026-07-10", "Spotify", "-11.99"),
                tx("2026-08-10", "Spotify", "-11.99"),
                tx("2026-09-10", "Spotify", "-12.99"));
        assertTrue(detector.detect(txs).get(0).priceIncreased());
    }

    @Test
    void labelsVariableAmounts() {
        List<Transaction> txs = List.of(
                tx("2026-06-20", "Toronto Hydro", "-61.00"),
                tx("2026-07-20", "Toronto Hydro", "-88.40"),
                tx("2026-08-19", "Toronto Hydro", "-74.10"));
        RecurringCharge hydro = detector.detect(txs).get(0);
        assertTrue(hydro.variableAmount());
    }

    @Test
    void ignoresIrregularAndTooFewCharges() {
        List<Transaction> txs = List.of(
                tx("2026-06-01", "Uber", "-12.00"),
                tx("2026-06-04", "Uber", "-18.00"),
                tx("2026-07-21", "Uber", "-9.00"),
                tx("2026-06-15", "Gym", "-40.00"),
                tx("2026-07-15", "Gym", "-40.00"));
        assertTrue(detector.detect(txs).isEmpty());
    }

    @Test
    void ignoresIncome() {
        List<Transaction> txs = List.of(
                tx("2026-06-15", "Payroll", "2000.00"),
                tx("2026-07-15", "Payroll", "2000.00"),
                tx("2026-08-15", "Payroll", "2000.00"));
        assertTrue(detector.detect(txs).isEmpty());
    }

    @Test
    void detectsWeeklyAndYearly() {
        List<Transaction> txs = new ArrayList<>(List.of(
                tx("2026-08-01", "Farm box", "-30.00"),
                tx("2026-08-08", "Farm box", "-30.00"),
                tx("2026-08-15", "Farm box", "-30.00"),
                tx("2024-03-01", "Amazon Prime", "-99.00"),
                tx("2025-03-01", "Amazon Prime", "-99.00"),
                tx("2026-03-02", "Amazon Prime", "-99.00")));
        List<RecurringCharge> found = detector.detect(txs);
        assertEquals(2, found.size());
        assertEquals(Frequency.WEEKLY, found.get(0).frequency(), "sorted by yearly cost: 1,560 > 99");
        assertEquals(Frequency.YEARLY, found.get(1).frequency());
    }

    @Test
    void lowConfidenceWithLessThanThreeMonthsOfData() {
        assertTrue(detector.isLowConfidence(List.of(tx("2026-08-01", "A", "-1"), tx("2026-09-01", "A", "-1"))));
        assertFalse(detector.isLowConfidence(List.of(tx("2026-01-01", "A", "-1"), tx("2026-09-01", "A", "-1"))));
        assertTrue(detector.isLowConfidence(List.of()));
    }

    @Test
    void merchantNormalisation() {
        assertEquals("netflix", RecurringChargeDetector.normaliseMerchant("NETFLIX.COM 866-579-7172"));
        assertEquals("rogers wireless", RecurringChargeDetector.normaliseMerchant("Rogers Wireless Inc #123"));
    }
}
