package com.maplecfo.domain;

import java.util.List;
import java.util.Objects;

/**
 * Composite pattern: the "leaf" role.
 * For liability accounts (credit card, loan) the balance is the positive
 * amount owed, and {@link #getValue()} returns it as a negative number.
 */
public class Account implements AssetComponent {

    private final String id;
    private final String name;
    private final AccountType type;
    private final String institution;
    private Money balance;

    public Account(String id, String name, AccountType type, String institution, Money balance) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.institution = institution == null ? "" : institution;
        this.balance = Objects.requireNonNull(balance, "balance");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Money getValue() {
        return isLiability() ? balance.negate() : balance;
    }

    @Override
    public List<AssetComponent> getChildren() {
        return List.of();
    }

    public boolean isLiability() {
        return type.isLiability();
    }

    public void updateBalance(Money newBalance) {
        this.balance = Objects.requireNonNull(newBalance, "balance");
    }

    public String getId() {
        return id;
    }

    public AccountType getType() {
        return type;
    }

    public String getInstitution() {
        return institution;
    }

    public Money getBalance() {
        return balance;
    }

    @Override
    public String toString() {
        return name + " (" + type + "): " + balance;
    }
}
