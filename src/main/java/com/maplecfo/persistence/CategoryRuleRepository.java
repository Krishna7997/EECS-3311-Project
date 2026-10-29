package com.maplecfo.persistence;

import com.maplecfo.categorization.CategoryRule;

import java.util.List;

/** Rules learned from the user's category corrections (F02), so they survive a restart. */
public interface CategoryRuleRepository {

    /** Saves a rule, replacing any learned rule with the same keyword. */
    void save(CategoryRule rule);

    List<CategoryRule> findAll();
}
