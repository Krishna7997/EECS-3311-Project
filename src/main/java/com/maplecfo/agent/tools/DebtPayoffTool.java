package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.DebtPayoffPlanner;
import com.maplecfo.planning.PayoffMethod;
import com.maplecfo.planning.PayoffPlan;

/** Compares avalanche and snowball payoff plans for the user's debts. */
public class DebtPayoffTool extends AbstractFinanceTool {

    private final DebtPayoffPlanner planner;

    public DebtPayoffTool(DebtPayoffPlanner planner) {
        this.planner = planner;
    }

    @Override public String name() { return "debt_payoff"; }

    @Override public String description() {
        return "Debt-free date, months and total interest for the avalanche and snowball methods, with an optional extra monthly payment.";
    }

    @Override public JsonNode schema() {
        return schemaOf("extra_monthly:number::Extra amount paid each month (default 0)");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        Money extra = money(args, "extra_monthly", Money.zero());
        ObjectNode out = obj();
        StringBuilder summary = new StringBuilder();
        for (PayoffMethod m : PayoffMethod.values()) {
            planner.setStrategy(m.toStrategy());
            PayoffPlan p = planner.plan(extra);
            if (p.isDebtFreeAlready()) {
                out.put("summary", "You have no debts recorded.");
                return out;
            }
            ObjectNode o = out.putObject(m.name().toLowerCase());
            o.put("months", p.monthsToDebtFree());
            o.put("debt_free_month", p.completes() ? p.debtFreeMonth().toString() : "not within 50 years");
            o.put("total_interest", p.totalInterest().toString());
            summary.append(m.name().charAt(0)).append(m.name().substring(1).toLowerCase()).append(": ")
                    .append(p.completes() ? "debt-free in " + p.monthsToDebtFree() + " months (" + p.debtFreeMonth() + ")"
                            : "not paid off within 50 years")
                    .append(", total interest ").append(p.totalInterest()).append(". ");
        }
        out.put("extra_monthly", extra.toString());
        out.put("summary", summary.toString().trim());
        return out;
    }
}
