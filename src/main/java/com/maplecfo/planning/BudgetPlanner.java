package com.maplecfo.planning;

import com.maplecfo.domain.Budget;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.services.TransactionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F17 (new in Stage 2): budget suggestions for a month from the three complete months before it. The numbers are
 * worked out here; the AI monthly review decides which ones to propose and explains why.
 */
public class BudgetPlanner {

    /** Categories that are not day-to-day spending, so they get no suggested budget. */
    static final Set<Category> NOT_BUDGETED = Set.of(Category.INCOME, Category.TRANSFER, Category.UNCATEGORIZED,
            Category.RENT);
    static final int MONTHS = 3;

    /**
     * @param suggested the three-month average rounded up to the next $10
     * @param current   the budget already set for that month, or null
     */
    public record Suggestion(Category category, Money threeMonthAverage, Money lastMonth, Money current, Money suggested) {
        public boolean changesBudget() {
            return current == null || !current.equals(suggested);
        }
    }

    private final TransactionService txService;
    private final BudgetService budgets;

    public BudgetPlanner(TransactionService txService, BudgetService budgets) {
        this.txService = txService;
        this.budgets = budgets;
    }

    /** Suggestions for {@code month}, biggest spending first. */
    public List<Suggestion> suggest(YearMonth month) {
        Map<Category, Money> total = new EnumMap<>(Category.class);
        Map<Category, Money> last = new EnumMap<>(Category.class);
        for (int i = 1; i <= MONTHS; i++) {
            YearMonth m = month.minusMonths(i);
            for (Transaction t : txService.findByMonth(m)) {
                if (!t.isExpense() || NOT_BUDGETED.contains(t.getCategory())) continue;
                total.merge(t.getCategory(), t.getAmount().abs(), Money::plus);
                if (i == 1) last.merge(t.getCategory(), t.getAmount().abs(), Money::plus);
            }
        }
        List<Suggestion> out = new ArrayList<>();
        total.forEach((c, sum) -> {
            Money avg = sum.dividedBy(MONTHS);
            BigDecimal tens = avg.getAmount().divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN);
            Money current = budgets.getBudget(c, month).map(Budget::limit).orElse(null);
            out.add(new Suggestion(c, avg, last.getOrDefault(c, Money.zero()), current, Money.of(tens)));
        });
        out.sort(Comparator.comparing(Suggestion::threeMonthAverage).reversed());
        return out;
    }
}
