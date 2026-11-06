package com.maplecfo.services;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Which transactions to show (F01/F02): any combination of a date range, account, category, money in or out,
 * amount range and text. A null field means "any".
 */
public record TransactionFilter(LocalDate from, LocalDate to, String accountId, Category category, Direction direction,
                                Money minAmount, Money maxAmount, String text) {

    public enum Direction { IN, OUT }

    public static TransactionFilter all() {
        return new TransactionFilter(null, null, null, null, null, null, null, null);
    }

    public TransactionFilter between(LocalDate f, LocalDate t) {
        return new TransactionFilter(f, t, accountId, category, direction, minAmount, maxAmount, text);
    }

    public TransactionFilter account(String a) {
        return new TransactionFilter(from, to, a, category, direction, minAmount, maxAmount, text);
    }

    public TransactionFilter category(Category c) {
        return new TransactionFilter(from, to, accountId, c, direction, minAmount, maxAmount, text);
    }

    public TransactionFilter direction(Direction d) {
        return new TransactionFilter(from, to, accountId, category, d, minAmount, maxAmount, text);
    }

    public TransactionFilter amounts(Money min, Money max) {
        return new TransactionFilter(from, to, accountId, category, direction, min, max, text);
    }

    public TransactionFilter text(String t) {
        return new TransactionFilter(from, to, accountId, category, direction, minAmount, maxAmount, t);
    }

    /** Plain-language description, e.g. "dining, money out, $30.00 or more, 2026-10-01 to 2026-10-31". */
    public String describe() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (text != null && !text.isBlank()) parts.add("\"" + text.trim() + "\"");
        if (category != null) parts.add(category.name().toLowerCase().replace('_', ' '));
        if (direction != null) parts.add(direction == Direction.IN ? "money in" : "money out");
        if (accountId != null && !accountId.isBlank()) parts.add("account " + accountId);
        if (minAmount != null && maxAmount != null) parts.add("between " + minAmount + " and " + maxAmount);
        else if (minAmount != null) parts.add(minAmount + " or more");
        else if (maxAmount != null) parts.add("up to " + maxAmount);
        if (from != null && to != null) parts.add(from + " to " + to);
        else if (from != null) parts.add("from " + from);
        else if (to != null) parts.add("until " + to);
        return parts.isEmpty() ? "all transactions" : String.join(", ", parts);
    }

    public boolean matches(Transaction t) {
        if (from != null && t.getDate().isBefore(from)) return false;
        if (to != null && t.getDate().isAfter(to)) return false;
        if (accountId != null && !accountId.isBlank() && !t.getAccountId().equalsIgnoreCase(accountId)) return false;
        if (category != null && t.getCategory() != category) return false;
        if (direction == Direction.IN && t.isExpense()) return false;
        if (direction == Direction.OUT && !t.isExpense()) return false;
        Money size = t.getAmount().abs();
        if (minAmount != null && size.isLessThan(minAmount)) return false;
        if (maxAmount != null && size.isGreaterThan(maxAmount)) return false;
        if (text != null && !text.isBlank()
                && !Transaction.normalise(t.getDescription()).contains(Transaction.normalise(text))) return false;
        return true;
    }

    /** The matching transactions, newest first. */
    public List<Transaction> apply(List<Transaction> all) {
        return all.stream().filter(this::matches)
                .sorted(Comparator.comparing(Transaction::getDate).reversed()).toList();
    }

    /** [money in, money out] of a list of transactions. */
    public static Money[] totals(List<Transaction> txs) {
        Money in = Money.zero();
        Money out = Money.zero();
        for (Transaction t : txs) {
            if (t.isExpense()) out = out.plus(t.getAmount().abs());
            else in = in.plus(t.getAmount());
        }
        return new Money[] {in, out};
    }
}
