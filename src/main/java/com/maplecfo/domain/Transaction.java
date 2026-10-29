package com.maplecfo.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One bank or card transaction. Expenses have a negative amount and income a
 * positive one. The category can change (F02); everything else is fixed.
 */
public class Transaction {

    private final String id;
    private final LocalDate date;
    private final String description;
    private final String merchant;
    private final Money amount;
    private final String accountId;
    private Category category;
    private CategorySource categorySource;

    public Transaction(String id, LocalDate date, String description, String merchant,
                       Money amount, String accountId) {
        this.id = Objects.requireNonNull(id, "id");
        this.date = Objects.requireNonNull(date, "date");
        this.description = description == null ? "" : description.trim();
        this.merchant = merchant == null || merchant.isBlank() ? this.description : merchant.trim();
        this.amount = Objects.requireNonNull(amount, "amount");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.category = Category.UNCATEGORIZED;
        this.categorySource = CategorySource.NONE;
    }

    /**
     * Identifies the same real-world transaction across overlapping CSV exports:
     * date + amount + normalised description + account, hashed with SHA-256.
     */
    public String fingerprint() {
        String key = date + "|" + amount.getAmount().toPlainString() + "|"
                + normalise(description) + "|" + accountId;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Lower-case letters and digits only, single spaces: "NETFLIX.COM  " becomes "netflix com". */
    public static String normalise(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    public boolean isExpense() {
        return amount.isNegative();
    }

    public void setCategory(Category category, CategorySource source) {
        this.category = Objects.requireNonNull(category, "category");
        this.categorySource = Objects.requireNonNull(source, "source");
    }

    public String getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public String getMerchant() {
        return merchant;
    }

    public Money getAmount() {
        return amount;
    }

    public String getAccountId() {
        return accountId;
    }

    public Category getCategory() {
        return category;
    }

    public CategorySource getCategorySource() {
        return categorySource;
    }

    @Override
    public String toString() {
        return date + " " + description + " " + amount + " [" + category + "]";
    }
}
