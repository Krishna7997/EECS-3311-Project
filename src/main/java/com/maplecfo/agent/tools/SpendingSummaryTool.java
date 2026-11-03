package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.services.TransactionService;

import java.time.Clock;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/** Spending by category for one month. */
public class SpendingSummaryTool extends AbstractFinanceTool {

    private final TransactionService txService;
    private final Clock clock;

    public SpendingSummaryTool(TransactionService txService, Clock clock) {
        this.txService = txService;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "spending_summary";
    }

    @Override
    public String description() {
        return "Total spending, income and spending per category for one month (default: this month).";
    }

    @Override
    public JsonNode schema() {
        return schemaOf("month:string::Month as YYYY-MM, e.g. 2026-10");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        YearMonth m = month(args, "month", YearMonth.now(clock));
        Map<Category, Money> byCategory = new EnumMap<>(Category.class);
        Money spent = Money.zero();
        Money income = Money.zero();
        for (Transaction t : txService.findByMonth(m)) {
            if (t.getCategory() == Category.TRANSFER) continue;
            if (t.isExpense()) {
                byCategory.merge(t.getCategory(), t.getAmount().abs(), Money::plus);
                spent = spent.plus(t.getAmount().abs());
            } else {
                income = income.plus(t.getAmount());
            }
        }
        ObjectNode out = obj();
        out.put("month", m.toString());
        out.put("total_spent", spent.toString());
        out.put("total_income", income.toString());
        ObjectNode cats = out.putObject("by_category");
        byCategory.entrySet().stream().sorted(Map.Entry.<Category, Money>comparingByValue().reversed())
                .forEach(e -> cats.put(e.getKey().name(), e.getValue().toString()));
        String top = byCategory.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(e -> " The biggest category was " + e.getKey() + " at " + e.getValue() + ".").orElse("");
        out.put("summary", byCategory.isEmpty() ? "No spending recorded for " + m + "."
                : "In " + m + " you spent " + spent + " and received " + income + " (transfers excluded)." + top);
        return out;
    }
}
