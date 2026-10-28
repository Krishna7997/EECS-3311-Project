package com.maplecfo.persistence.memory;

import com.maplecfo.domain.Budget;
import com.maplecfo.domain.Category;
import com.maplecfo.persistence.BudgetRepository;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryBudgetRepository implements BudgetRepository {

    private final Map<String, Budget> budgets = new LinkedHashMap<>();

    private static String key(Category c, YearMonth m) {
        return c + "|" + m;
    }

    @Override
    public void save(Budget budget) {
        budgets.put(key(budget.category(), budget.month()), budget);
    }

    @Override
    public Optional<Budget> find(Category category, YearMonth month) {
        return Optional.ofNullable(budgets.get(key(category, month)));
    }

    @Override
    public List<Budget> findByMonth(YearMonth month) {
        return budgets.values().stream().filter(b -> b.month().equals(month)).toList();
    }

    @Override
    public void delete(Category category, YearMonth month) {
        budgets.remove(key(category, month));
    }
}
