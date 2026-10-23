package com.maplecfo.domain;

import java.util.Objects;

/** A debt such as a credit card or student loan (F08). APR is a percentage, e.g. 19.99. */
public class Debt {

    private final String id;
    private final String name;
    private final double apr;
    private final Money minPayment;
    private final String accountId;
    private Money balance;

    public Debt(String id, String name, Money balance, double apr, Money minPayment, String accountId) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.minPayment = Objects.requireNonNull(minPayment, "minPayment");
        if (apr < 0 || apr > 60) {
            throw new IllegalArgumentException("APR must be between 0% and 60%, was " + apr);
        }
        if (balance.isNegative()) {
            throw new IllegalArgumentException("Debt balance cannot be negative");
        }
        if (minPayment.isNegative()) {
            throw new IllegalArgumentException("Minimum payment cannot be negative");
        }
        this.apr = apr;
        this.accountId = accountId;
    }

    /** Interest charged on the current balance for one month. */
    public Money monthlyInterest() {
        return balance.times(apr / 100.0 / 12.0);
    }

    /** Auto-sync (F13) updates the balance from credit-card imports. */
    public void updateBalance(Money newBalance) {
        this.balance = Objects.requireNonNull(newBalance, "balance");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Money getBalance() { return balance; }
    public double getApr() { return apr; }
    public Money getMinPayment() { return minPayment; }
    public String getAccountId() { return accountId; }

    @Override
    public String toString() {
        return name + " " + balance + " @ " + apr + "%";
    }
}
