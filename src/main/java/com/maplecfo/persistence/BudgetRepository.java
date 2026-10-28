package com.maplecfo.persistence;

import com.maplecfo.domain.Budget;
import com.maplecfo.domain.Category;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** Monthly budgets, one per category and month (F04). */
public interface BudgetRepository {

    /** Saves the budget, replacing any budget for the same category and month. */
    void save(Budget budget);

    Optional<Budget> find(Category category, YearMonth month);

    List<Budget> findByMonth(YearMonth month);

    void delete(Category category, YearMonth month);
}
