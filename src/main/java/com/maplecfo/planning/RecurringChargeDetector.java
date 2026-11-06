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
        return detect(txs, null);
    }

    /**
     * Same as {@link #detect(List)}, but a predicted next date that is already
     * in the past is moved forward until it is on or after {@code today}.
     */
    public List<RecurringCharge> detect(List<Transaction> txs, LocalDate today) {
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
                frequencyOf(group).filter(f -> !isStopped(group, f, today))
                        .ifPresent(f -> result.add(build(group, f, today)));
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

    private RecurringCharge build(List<Transaction> sorted, Frequency f, LocalDate today) {
        Transaction latest = sorted.get(sorted.size() - 1);
        List<Transaction> earlier = sorted.subList(0, sorted.size() - 1);

        Money average = averageOf(sorted);
        Money earlierAverage = averageOf(earlier);
        boolean variable = spread(earlier, earlierAverage) > VARIABLE_AMOUNT_THRESHOLD;
        // A bill that already varies (hydro, gas) has no fixed price, so "price went up" would be misleading.
        boolean increased = !variable && latest.getAmount().abs()
                .isGreaterThan(earlierAverage.times(1 + PRICE_INCREASE_THRESHOLD));

        // A price change that already happened a few months ago shows up as a step: the latest charges share
        // one price and the ones before share a lower one (Netflix 16.99 x5, then 18.99 x4). Without this check
        // the mix of the two prices looked like a "variable amount".
        int samePrice = 1;
        while (samePrice < sorted.size()
                && sorted.get(sorted.size() - 1 - samePrice).getAmount().abs().equals(latest.getAmount().abs())) {
            samePrice++;
        }
        if (samePrice < sorted.size()) {
            List<Transaction> before = sorted.subList(0, sorted.size() - samePrice);
            Money beforeAverage = averageOf(before);
            if (spread(before, beforeAverage) <= VARIABLE_AMOUNT_THRESHOLD / 2 && latest.getAmount().abs()
                    .isGreaterThan(beforeAverage.times(1 + PRICE_INCREASE_THRESHOLD))) {
                increased = true;
                variable = false;
            }
        }

        LocalDate next = step(latest.getDate(), f);
        while (today != null && next.isBefore(today)) {
            next = step(next, f);
        }
        // after a price increase the new price is what you will pay from now on
        Money usual = increased ? latest.getAmount().abs() : average;
        return new RecurringCharge(latest.getMerchant(), usual, f, next, increased, variable, sorted);
    }

    /** A charge that missed two expected dates was most likely cancelled, so it is no longer listed. */
    private static boolean isStopped(List<Transaction> sorted, Frequency f, LocalDate today) {
        if (today == null) {
            return false;
        }
        LocalDate last = sorted.get(sorted.size() - 1).getDate();
        return step(step(last, f), f).isBefore(today);
    }

    private static LocalDate step(LocalDate d, Frequency f) {
        return switch (f) {
            case WEEKLY -> d.plusWeeks(1);
            case MONTHLY -> d.plusMonths(1);
            case YEARLY -> d.plusYears(1);
        };
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
