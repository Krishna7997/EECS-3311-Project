package com.maplecfo.categorization;

import com.maplecfo.agent.PromptBuilder;
import com.maplecfo.agent.llm.LlmException;
import com.maplecfo.agent.llm.MockLlmClient;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.testutil.FakeLlm;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LlmCategorizerTest {

    private int ids;

    private Transaction tx(String d) {
        return new Transaction("t" + (ids++), LocalDate.of(2026, 10, 2), d, null, Money.of("-12.00"), "a");
    }

    @Test
    void acceptsOnlyAnExactCategoryName() {
        assertEquals(Optional.of(Category.DINING), LlmCategorizer.parseCategory(" dining. "));
        assertEquals(Optional.empty(), LlmCategorizer.parseCategory("I think it's DINING"));
        assertEquals(Optional.empty(), LlmCategorizer.parseCategory("FOOD"));
        assertEquals(Optional.empty(), LlmCategorizer.parseCategory("UNCATEGORIZED"));
    }

    @Test
    void aiIsOnlyAskedWhenNoRuleMatchesAndOncePerMerchant() {
        FakeLlm llm = new FakeLlm("SHOPPING");
        CategorizationService s = new CategorizationService(RuleBasedCategorizer.withDefaultRules(),
                List.of(new LlmCategorizer(llm, new PromptBuilder())));
        Transaction known = tx("STARBUCKS #12");
        Transaction a = tx("QUICK MART 0091");
        Transaction b = tx("QUICK MART 0091");
        s.categorizeAll(List.of(known, a, b));
        assertEquals(CategorySource.RULE, known.getCategorySource());
        assertEquals(Category.SHOPPING, a.getCategory());
        assertEquals(CategorySource.AI, b.getCategorySource());
        assertEquals(1, llm.received.size(), "one call for one merchant");
        assertTrue(llm.received.get(0).get(1).content().contains("QUICK MART"));
    }

    @Test
    void badAnswerOrOutageLeavesTheTransactionForReview() {
        Transaction t1 = tx("ODD SHOP 1");
        Transaction t2 = tx("OTHER ODD 2");
        new CategorizationService(RuleBasedCategorizer.withDefaultRules(),
                List.of(new LlmCategorizer(new FakeLlm("Probably groceries?", new LlmException("down", true)),
                        new PromptBuilder()))).categorizeAll(List.of(t1, t2));
        assertEquals(Category.UNCATEGORIZED, t1.getCategory());
        assertEquals(Category.UNCATEGORIZED, t2.getCategory());
    }

    @Test
    void demoModeSkipsTheAi() {
        Transaction t = tx("ODD SHOP");
        new LlmCategorizer(new MockLlmClient(List.of()), new PromptBuilder()).categorize(t);
        assertEquals(Category.UNCATEGORIZED, t.getCategory());
    }
}
