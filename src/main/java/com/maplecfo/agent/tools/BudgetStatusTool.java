package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.planning.BudgetService;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Spending against each budget for one month. */
public class BudgetStatusTool extends AbstractFinanceTool {

    private final BudgetService budgets;
    private final Clock clock;

    public BudgetStatusTool(BudgetService budgets, Clock clock) {
        this.budgets = budgets;
        this.clock = clock;
    }

    @Override public String name() { return "budget_status"; }

    @Override public String description() {
        return "The user's monthly budgets: limit, spent, remaining and percent used for each category.";
    }

    @Override public JsonNode schema() {
        return schemaOf("month:string::Month as YYYY-MM (default: this month)");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        YearMonth m = month(args, "month", YearMonth.now(clock));
        List<BudgetStatus> statuses = budgets.getStatus(m);
        ObjectNode out = obj();
        out.put("month", m.toString());
        ArrayNode list = out.putArray("budgets");
        List<String> over = new ArrayList<>();
        for (BudgetStatus s : statuses) {
            ObjectNode b = list.addObject();
            b.put("category", s.budget().category().name());
            b.put("limit", s.budget().limit().toString());
            b.put("spent", s.spent().toString());
            b.put("remaining", s.remaining().toString());
            b.put("percent_used", Math.round(s.percentUsed()));
            if (s.isExceeded()) over.add(s.budget().category() + " (" + s.spent() + " of " + s.budget().limit() + ")");
        }
        out.put("summary", statuses.isEmpty() ? "There are no budgets set for " + m + "."
                : statuses.size() + " budgets for " + m + ". "
                + (over.isEmpty() ? "None are over the limit." : "Over the limit: " + String.join(", ", over) + "."));
        return out;
    }
}
