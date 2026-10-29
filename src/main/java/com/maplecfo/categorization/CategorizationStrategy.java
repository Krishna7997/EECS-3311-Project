package com.maplecfo.categorization;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Transaction;

import java.util.Optional;

/**
 * Strategy pattern: one way of choosing a category. {@link CategorizationService}
 * runs the strategies in order (rules first, then the LLM) and stops at the
 * first one that answers.
 */
public interface CategorizationStrategy {

    /** The category, or empty when this strategy cannot decide. */
    Optional<Category> categorize(Transaction t);

    /** What to record as the category's source when this strategy answers. */
    CategorySource source();
}
