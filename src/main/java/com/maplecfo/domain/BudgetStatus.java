package com.maplecfo.domain;

import java.util.Objects;

/** How much of a budget has been spent this month. {@code spent} is a positive amount. */
public record BudgetStatus(Budget budget, Money spent) {

    public BudgetStatus {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(spent, "spent");
    }

    public Money remaining() {
        return budget.limit().minus(spent);
    }

    /** Percentage of the limit used, e.g. 85.0 for 85%. */
    public double percentUsed() {
        return spent.getAmount().doubleValue() / budget.limit().getAmount().doubleValue() * 100.0;
    }

    public boolean isExceeded() {
        return spent.isGreaterThan(budget.limit());
    }
}
