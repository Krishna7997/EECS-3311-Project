package com.maplecfo.categorization;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.Transaction;
import com.maplecfo.persistence.CategoryRuleRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * F02 categorization: runs an ordered chain of strategies (rules first, then
 * the LLM) on each uncategorized transaction. Transactions no strategy can
 * categorize stay UNCATEGORIZED and show up as "needs review".
 */
public class CategorizationService {

    private final List<CategorizationStrategy> strategies;
    private final RuleBasedCategorizer rules;
    private CategoryRuleRepository ruleRepo;

    public CategorizationService(RuleBasedCategorizer rules, List<CategorizationStrategy> laterStrategies) {
        this.rules = Objects.requireNonNull(rules);
        this.strategies = new java.util.ArrayList<>();
        this.strategies.add(rules);
        this.strategies.addAll(laterStrategies);
    }

    /** Rules only (no LLM), e.g. when no API key is configured. */
    public static CategorizationService rulesOnly() {
        return new CategorizationService(RuleBasedCategorizer.withDefaultRules(), List.of());
    }

    /** Categorizes the transactions that are still UNCATEGORIZED, in place. */
    public List<Transaction> categorizeAll(List<Transaction> txs) {
        for (Transaction t : txs) {
            if (t.getCategory() == Category.UNCATEGORIZED) {
                categorize(t);
            }
        }
        return txs;
    }

    private void categorize(Transaction t) {
        for (CategorizationStrategy s : strategies) {
            Optional<Category> c;
            try {
                c = s.categorize(t);
            } catch (RuntimeException e) {
                c = Optional.empty(); // e.g. LLM unavailable: fall through, leave for review
            }
            if (c.isPresent() && c.get() != Category.UNCATEGORIZED) {
                t.setCategory(c.get(), s.source());
                return;
            }
        }
    }

    /** Loads previously learned rules and saves new ones there from now on. */
    public void useRuleRepository(CategoryRuleRepository repo) {
        this.ruleRepo = Objects.requireNonNull(repo);
        repo.findAll().forEach(rules::addRule);
    }

    /** Learns a merchant rule from a user correction. */
    public void learn(Transaction t, Category c) {
        rules.learnFromCorrection(t, c).ifPresent(rule -> {
            if (ruleRepo != null) {
                ruleRepo.save(rule);
            }
        });
    }

    public RuleBasedCategorizer getRules() {
        return rules;
    }
}
