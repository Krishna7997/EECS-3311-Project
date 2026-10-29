package com.maplecfo.categorization;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.Transaction;

import java.util.Objects;

/** "If the description contains this keyword, use this category." */
public record CategoryRule(String keyword, Category category, boolean learned) {

    public CategoryRule {
        Objects.requireNonNull(category, "category");
        keyword = Transaction.normalise(Objects.requireNonNull(keyword, "keyword"));
        if (keyword.isEmpty()) {
            throw new IllegalArgumentException("Rule keyword must contain letters or digits");
        }
    }

    /** Matches whole words, so "metro" matches "METRO #123" but not "metropolitan". */
    public boolean matches(Transaction t) {
        String text = " " + Transaction.normalise(t.getDescription() + " " + t.getMerchant()) + " ";
        return text.contains(" " + keyword + " ");
    }
}
