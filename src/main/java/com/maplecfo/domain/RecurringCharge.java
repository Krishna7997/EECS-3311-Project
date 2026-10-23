package com.maplecfo.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A subscription or other repeating charge found by the detector (F05).
 *
 * @param averageAmount  positive average charge
 * @param priceIncreased the latest charge is more than 5% above the earlier average
 * @param variableAmount the amounts vary (for example a utility bill), so the average is an estimate
 */
public record RecurringCharge(String merchant, Money averageAmount, Frequency frequency,
                              LocalDate nextExpectedDate, boolean priceIncreased,
                              boolean variableAmount, List<Transaction> occurrences) {

    public RecurringCharge {
        Objects.requireNonNull(merchant, "merchant");
        Objects.requireNonNull(averageAmount, "averageAmount");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(nextExpectedDate, "nextExpectedDate");
        occurrences = List.copyOf(occurrences);
        if (occurrences.size() < 2) {
            throw new IllegalArgumentException("A recurring charge needs at least two occurrences");
        }
    }

    /** Estimated cost per year, e.g. a $16.99 monthly charge costs $203.88 a year. */
    public Money yearlyCost() {
        return switch (frequency) {
            case WEEKLY -> averageAmount.times(52);
            case MONTHLY -> averageAmount.times(12);
            case YEARLY -> averageAmount;
        };
    }

    /** The most recent charge amount (positive). */
    public Money latestAmount() {
        return occurrences.get(occurrences.size() - 1).getAmount().abs();
    }
}
