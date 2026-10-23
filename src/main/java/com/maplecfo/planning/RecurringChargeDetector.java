package com.maplecfo.planning;

import com.maplecfo.domain.Frequency;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.domain.Transaction;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * F05 recurring charge and subscription detection.
 *
 * <p>Steps: keep expenses, normalise merchant names, group by merchant, keep
 * groups with at least {@code minOccurrences} charges at a regular interval
 * (weekly, monthly or yearly, within +/- 3 days), then compute the average,
 * the next expected date and whether the price went up.
 */
public class RecurringChargeDetector {

    public static final int DEFAULT_MIN_OCCURRENCES = 3;
    static final int TOLERANCE_DAYS = 3;
    static final double PRICE_INCREASE_THRESHOLD = 0.05;
    static final double VARIABLE_AMOUNT_THRESHOLD = 0.10;
    static final int LOW_CONFIDENCE_DAYS = 90;

    private static final Set<String> NOISE_WORDS = Set.of("com", "ca", "www", "inc", "ltd", "corp", "co");

    private final int minOccurrences;

    public RecurringChargeDetector() {
        this(DEFAULT_MIN_OCCURRENCES);
    }

    public RecurringChargeDetector(int minOccurrences) {
        if (minOccurrences < 2) {
            throw new IllegalArgumentException("minOccurrences must be at least 2");
        }
        this.minOccurrences = minOccurrences;
    }

    public List<RecurringCharge> detect(List<Transaction> txs) {
        Map<String, List<Transaction>> groups = new LinkedHashMap<>();
        for (Transaction t : txs) {
            if (t.isExpense()) {
                String key = normaliseMerchant(t.getMerchant());
                if (!key.isEmpty()) {
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
                }
            }
        }

        List<RecurringCharge> result = new ArrayList<>();
        for (List<Transaction> group : groups.values()) {
            if (group.size() >= minOccurrences) {
                group.sort(Comparator.comparing(Transaction::getDate));
                frequencyOf(group).ifPresent(f -> result.add(build(group, f)));
            }
        }
        result.sort(Comparator.comparing(RecurringCharge::yearlyCost).reversed());
        return result;
    }

    /** True when the transactions cover less than about three months, so results are less reliable. */
    public boolean isLowConfidence(List<Transaction> txs) {
        if (txs.isEmpty()) {
            return true;
        }
        LocalDate first = txs.stream().map(Transaction::getDate).min(LocalDate::compareTo).orElseThrow();
        LocalDate last = txs.stream().map(Transaction::getDate).max(LocalDate::compareTo).orElseThrow();
        return ChronoUnit.DAYS.between(first, last) < LOW_CONFIDENCE_DAYS;
    }

    /** "NETFLIX.COM 866-579" and "Netflix.com" both become "netflix". */
    static String normaliseMerchant(String merchant) {
        String cleaned = merchant.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        StringBuilder sb = new StringBuilder();
        for (String word : cleaned.split(" ")) {
            if (word.isEmpty() || NOISE_WORDS.contains(word) || word.chars().anyMatch(Character::isDigit)) {
                continue;
            }
            if (sb.length() > 0) sb.append(' ');
            sb.append(word);
        }
        return sb.toString();
    }

    private Optional<Frequency> frequencyOf(List<Transaction> sorted) {
        for (Frequency f : Frequency.values()) {
            boolean allMatch = true;
            for (int i = 1; i < sorted.size(); i++) {
                long gap = ChronoUnit.DAYS.between(sorted.get(i - 1).getDate(), sorted.get(i).getDate());
                if (!fits(gap, f)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    private static boolean fits(long gapDays, Frequency f) {
        return switch (f) {
            case WEEKLY -> Math.abs(gapDays - 7) <= TOLERANCE_DAYS;
            // calendar months are 28-31 days long
            case MONTHLY -> gapDays >= 28 - TOLERANCE_DAYS && gapDays <= 31 + TOLERANCE_DAYS;
            case YEARLY -> gapDays >= 365 - TOLERANCE_DAYS && gapDays <= 366 + TOLERANCE_DAYS;
        };
    }

    private RecurringCharge build(List<Transaction> sorted, Frequency f) {
        Transaction latest = sorted.get(sorted.size() - 1);
        List<Transaction> earlier = sorted.subList(0, sorted.size() - 1);

        Money average = averageOf(sorted);
        Money earlierAverage = averageOf(earlier);
        boolean increased = latest.getAmount().abs()
                .isGreaterThan(earlierAverage.times(1 + PRICE_INCREASE_THRESHOLD));
        boolean variable = spread(earlier, earlierAverage) > VARIABLE_AMOUNT_THRESHOLD;

        LocalDate next = switch (f) {
            case WEEKLY -> latest.getDate().plusWeeks(1);
            case MONTHLY -> latest.getDate().plusMonths(1);
            case YEARLY -> latest.getDate().plusYears(1);
        };
        return new RecurringCharge(latest.getMerchant(), average, f, next, increased, variable, sorted);
    }

    private static Money averageOf(List<Transaction> txs) {
        Money total = Money.zero();
        for (Transaction t : txs) {
            total = total.plus(t.getAmount().abs());
        }
        return total.dividedBy(txs.size());
    }

    /** (max - min) / average of the amounts. */
    private static double spread(List<Transaction> txs, Money average) {
        if (average.isZero()) {
            return 0;
        }
        Money max = txs.stream().map(t -> t.getAmount().abs()).max(Money::compareTo).orElseThrow();
        Money min = txs.stream().map(t -> t.getAmount().abs()).min(Money::compareTo).orElseThrow();
        return max.minus(min).getAmount().doubleValue() / average.getAmount().doubleValue();
    }
}
