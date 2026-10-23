package com.maplecfo.domain;

import java.time.LocalDate;
import java.util.Objects;

/** A savings goal such as "Emergency fund $5,000 by June" (F09). */
public class SavingsGoal {

    private final String id;
    private final String name;
    private final Money target;
    private final LocalDate deadline;
    private final String linkedAccountId;
    private Money saved;

    public SavingsGoal(String id, String name, Money target, Money saved,
                       LocalDate deadline, String linkedAccountId) {
        this.id = Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Goal name must not be blank");
        }
        this.name = name;
        this.target = Objects.requireNonNull(target, "target");
        if (!target.isPositive()) {
            throw new IllegalArgumentException("Goal target must be greater than zero");
        }
        this.saved = saved == null ? Money.zero() : saved;
        if (this.saved.isNegative()) {
            throw new IllegalArgumentException("Saved amount cannot be negative");
        }
        this.deadline = deadline;
        this.linkedAccountId = linkedAccountId;
    }

    /** Fraction of the target saved, from 0.0 to 1.0 (capped). */
    public double progress() {
        double p = saved.getAmount().doubleValue() / target.getAmount().doubleValue();
        return Math.min(1.0, p);
    }

    public Money remaining() {
        return target.minus(saved).max(Money.zero());
    }

    public boolean isComplete() {
        return !saved.isLessThan(target);
    }

    public void deposit(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Deposit must be greater than zero");
        }
        saved = saved.plus(amount);
    }

    /** Used by undo: removes a previous deposit. */
    public void withdraw(Money amount) {
        saved = saved.minus(amount).max(Money.zero());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Money getTarget() { return target; }
    public Money getSaved() { return saved; }
    public LocalDate getDeadline() { return deadline; }
    public String getLinkedAccountId() { return linkedAccountId; }
}
