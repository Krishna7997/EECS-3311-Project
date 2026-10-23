package com.maplecfo.domain;

/** Spending categories. The LLM categorizer may only answer with one of these names. */
public enum Category {
    GROCERIES,
    DINING,
    TRANSPORT,
    GAS,
    RENT,
    UTILITIES,
    SUBSCRIPTIONS,
    SHOPPING,
    TRAVEL,
    INCOME,
    TRANSFER,
    OTHER,
    UNCATEGORIZED;

    /** Parses a category name case-insensitively; returns null when it is not a valid category. */
    public static Category parseOrNull(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.trim().toUpperCase().replace(' ', '_');
        for (Category c : values()) {
            if (c.name().equals(cleaned)) {
                return c;
            }
        }
        return null;
    }
}
