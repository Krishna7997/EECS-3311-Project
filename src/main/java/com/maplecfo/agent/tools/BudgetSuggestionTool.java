package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.planning.BudgetPlanner;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

/** Suggested budgets for a month from the three months before it (F17). Suggests only; nothing is changed. */
public class BudgetSuggestionTool extends AbstractFinanceTool {

    private final BudgetPlanner planner;
    private final Clock clock;

    public BudgetSuggestionTool(BudgetPlanner planner, Clock clock) {
        this.planner = planner;
        this.clock = clock;
    }

    @Override public String name() { return "suggest_budgets"; }

    @Override public String description() {
        return "Suggested monthly budgets per spending category for a month: the average of the three complete months "
                + "before it, rounded up to the next $10, with last month's spending and the budget already set (if any). "
                + "Rent, income and transfers are not included. To set one, call propose_action.";
    }

    @Override public JsonNode schema() {
        return schemaOf("month:string::Month the budgets are for, YYYY-MM (default: next month)");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        YearMonth month = month(args, "month", YearMonth.now(clock).plusMonths(1));
        List<BudgetPlanner.Suggestion> list = planner.suggest(month);
        ObjectNode out = obj();
        out.put("month", month.toString());
        var arr = out.putArray("suggestions");
        StringBuilder summary = new StringBuilder("Suggested budgets for " + month + ": ");
        for (BudgetPlanner.Suggestion s : list) {
            ObjectNode n = arr.addObject();
            n.put("category", s.category().name());
            n.put("three_month_average", s.threeMonthAverage().toString());
            n.put("last_month", s.lastMonth().toString());
            n.put("current_budget", s.current() == null ? "none" : s.current().toString());
            n.put("suggested", s.suggested().toString());
            summary.append(s.category().name()).append(' ').append(s.suggested())
                    .append(s.current() == null ? " (none set)" : " (now " + s.current() + ")").append("; ");
        }
        out.put("summary", list.isEmpty() ? "Not enough spending history to suggest budgets for " + month + "."
                : summary.substring(0, summary.length() - 2) + ".");
        return out;
    }
}
