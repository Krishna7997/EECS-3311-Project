package com.maplecfo.domain;

import java.time.YearMonth;
import java.util.Objects;

/** A monthly spending limit for one category (F04). */
public record Budget(Category category, YearMonth month, Money limit) {

    public Budget {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(limit, "limit");
        if (!limit.isPositive()) {
            throw new IllegalArgumentException("Budget limit must be greater than zero");
        }
        if (category == Category.INCOME || category == Category.TRANSFER) {
            throw new IllegalArgumentException("Budgets apply to spending categories only");
        }
    }
}
