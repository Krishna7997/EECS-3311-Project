package com.maplecfo.reports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * F12 monthly report: computed figures plus a short narrative. The narrative
 * comes from the AI when available, otherwise from a template.
 *
 * @param spendingChange spending this month minus last month (positive = spent more)
 * @param narrativeByAi  true when the AI wrote the narrative, false for the template
 */
public record MonthlyReport(YearMonth month, Money income, Money spending, Money saved, double savingsRatePercent,
                            Map<Category, Money> spendingByCategory, List<BudgetStatus> budgets,
                            Money recurringPerMonth, List<String> priceIncreases, Money spendingChange,
                            int transactionCount, String narrative, boolean narrativeByAi) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public MonthlyReport {
        spendingByCategory = Map.copyOf(spendingByCategory);
        budgets = List.copyOf(budgets);
        priceIncreases = List.copyOf(priceIncreases);
    }

    public boolean hasData() {
        return transactionCount > 0;
    }

    public MonthlyReport withNarrative(String text, boolean byAi) {
        return new MonthlyReport(month, income, spending, saved, savingsRatePercent, spendingByCategory, budgets,
                recurringPerMonth, priceIncreases, spendingChange, transactionCount, text, byAi);
    }

    /** Only the computed figures, which is all the AI is given (Stage 1 F12 workflow). */
    public String figuresAsJson() {
        ObjectNode o = JSON.createObjectNode();
        o.put("month", month.toString());
        o.put("income", income.toString());
        o.put("spending", spending.toString());
        o.put("saved", saved.toString());
        o.put("savings_rate_percent", Math.round(savingsRatePercent));
        o.put("change_in_spending_vs_last_month", spendingChange.toString());
        ObjectNode cats = o.putObject("spending_by_category");
        spendingByCategory.entrySet().stream().sorted(Map.Entry.<Category, Money>comparingByValue().reversed())
                .forEach(e -> cats.put(e.getKey().name(), e.getValue().toString()));
        var b = o.putArray("budgets");
        budgets.forEach(s -> b.addObject().put("category", s.budget().category().name())
                .put("limit", s.budget().limit().toString()).put("spent", s.spent().toString())
                .put("over_limit", s.isExceeded()));
        o.put("recurring_charges_per_month", recurringPerMonth.toString());
        var p = o.putArray("price_increases");
        priceIncreases.forEach(p::add);
        return o.toString();
    }
}
