package com.maplecfo.categorization;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CategorizationServiceTest {

    private int ids;

    private Transaction tx(String description) {
        return new Transaction("t" + (ids++), LocalDate.of(2026, 10, 1), description, null, Money.of("-10.00"), "acc");
    }

    @Test
    void defaultRulesCoverCommonMerchants() {
        CategorizationService service = CategorizationService.rulesOnly();
        List<Transaction> txs = List.of(tx("LOBLAWS #1032 TORONTO"), tx("UBER EATS TORONTO"), tx("UBER *TRIP"),
                tx("NETFLIX.COM"), tx("PETRO-CANADA 4412"), tx("RANDOM SHOP 123"));
        service.categorizeAll(txs);
        assertEquals(Category.GROCERIES, txs.get(0).getCategory());
        assertEquals(Category.DINING, txs.get(1).getCategory(), "longer keyword 'uber eats' wins over 'uber'");
        assertEquals(Category.TRANSPORT, txs.get(2).getCategory());
        assertEquals(Category.SUBSCRIPTIONS, txs.get(3).getCategory());
        assertEquals(Category.GAS, txs.get(4).getCategory());
        assertEquals(Category.UNCATEGORIZED, txs.get(5).getCategory(), "no rule: left for review");
        assertEquals(CategorySource.RULE, txs.get(0).getCategorySource());
    }

    @Test
    void wholeWordMatchingAvoidsFalseHits() {
        CategoryRule metro = new CategoryRule("metro", Category.GROCERIES, false);
        assertTrue(metro.matches(tx("METRO #455")));
        assertFalse(metro.matches(tx("METROPOLITAN LIFE")));
    }

    @Test
    void learnedRuleBeatsDefaultRule() {
        CategorizationService service = CategorizationService.rulesOnly();
        Transaction costco = tx("COSTCO WHOLESALE #540");
        service.categorizeAll(List.of(costco));
        assertEquals(Category.GROCERIES, costco.getCategory());

        service.learn(costco, Category.SHOPPING); // the user says Costco is shopping for them
        Transaction next = tx("COSTCO WHOLESALE #540");
        service.categorizeAll(List.of(next));
        assertEquals(Category.SHOPPING, next.getCategory());
        assertEquals(1, service.getRules().getLearnedRules().size());
    }

    @Test
    void laterStrategyIsUsedOnlyWhenRulesFail() {
        CategorizationStrategy fakeAi = new CategorizationStrategy() {
            public Optional<Category> categorize(Transaction t) { return Optional.of(Category.OTHER); }
            public CategorySource source() { return CategorySource.AI; }
        };
        CategorizationService service = new CategorizationService(RuleBasedCategorizer.withDefaultRules(), List.of(fakeAi));
        Transaction known = tx("STARBUCKS 123");
        Transaction unknown = tx("SOMETHING ODD");
        service.categorizeAll(List.of(known, unknown));
        assertEquals(CategorySource.RULE, known.getCategorySource());
        assertEquals(Category.OTHER, unknown.getCategory());
        assertEquals(CategorySource.AI, unknown.getCategorySource());
    }

    @Test
    void failingStrategyLeavesTransactionForReview() {
        CategorizationStrategy broken = new CategorizationStrategy() {
            public Optional<Category> categorize(Transaction t) { throw new IllegalStateException("LLM down"); }
            public CategorySource source() { return CategorySource.AI; }
        };
        CategorizationService service = new CategorizationService(RuleBasedCategorizer.withDefaultRules(), List.of(broken));
        Transaction t = tx("SOMETHING ODD");
        service.categorizeAll(List.of(t));
        assertEquals(Category.UNCATEGORIZED, t.getCategory());
    }

    @Test
    void merchantKeywordStripsStoreNumbersAndCities() {
        assertEquals("starbucks", RuleBasedCategorizer.merchantKeyword("STARBUCKS #4521 TORONTO ON"));
        assertEquals("costco wholesale", RuleBasedCategorizer.merchantKeyword("COSTCO WHOLESALE W540"));
    }
}
