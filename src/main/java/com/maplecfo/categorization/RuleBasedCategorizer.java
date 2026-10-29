package com.maplecfo.categorization;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Categorizes by keyword rules: rules learned from the user's corrections
 * first, then built-in rules for common Canadian merchants. Within each group
 * longer keywords win, so "uber eats" (DINING) beats "uber" (TRANSPORT).
 */
public class RuleBasedCategorizer implements CategorizationStrategy {

    private final List<CategoryRule> rules = new ArrayList<>();

    public RuleBasedCategorizer(List<CategoryRule> rules) {
        rules.forEach(this::addRule);
    }

    public static RuleBasedCategorizer withDefaultRules() {
        return new RuleBasedCategorizer(DefaultRules.canadian());
    }

    @Override
    public synchronized Optional<Category> categorize(Transaction t) {
        for (CategoryRule r : rules) {
            if (r.matches(t)) {
                return Optional.of(r.category());
            }
        }
        return Optional.empty();
    }

    @Override
    public CategorySource source() {
        return CategorySource.RULE;
    }

    /**
     * Learns from a user correction: future transactions from the same
     * merchant get the corrected category.
     *
     * @return the new rule, or empty when the merchant has no usable name
     */
    public synchronized Optional<CategoryRule> learnFromCorrection(Transaction t, Category c) {
        String keyword = merchantKeyword(t.getMerchant());
        if (keyword.isEmpty()) {
            return Optional.empty();
        }
        rules.removeIf(r -> r.learned() && r.keyword().equals(keyword));
        CategoryRule rule = new CategoryRule(keyword, c, true);
        addRule(rule);
        return Optional.of(rule);
    }

    public synchronized void addRule(CategoryRule rule) {
        rules.add(rule);
        rules.sort(Comparator.comparing((CategoryRule r) -> !r.learned())
                .thenComparing(r -> -r.keyword().length()));
    }

    public synchronized List<CategoryRule> getRules() {
        return List.copyOf(rules);
    }

    public synchronized List<CategoryRule> getLearnedRules() {
        return rules.stream().filter(CategoryRule::learned).toList();
    }

    /** "STARBUCKS #4521 TORONTO ON" becomes "starbucks": the first words without digits, at most two. */
    static String merchantKeyword(String merchant) {
        List<String> words = new ArrayList<>();
        for (String w : Transaction.normalise(merchant).split(" ")) {
            if (w.isEmpty() || w.chars().anyMatch(Character::isDigit)) {
                if (!words.isEmpty()) break;
                continue;
            }
            words.add(w);
            if (words.size() == 2) break;
        }
        return String.join(" ", words);
    }
}
