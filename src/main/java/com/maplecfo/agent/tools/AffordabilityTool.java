package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.planning.AffordabilityAnalyzer;
import com.maplecfo.planning.AffordabilityResult;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** "Can I afford X by date Y?" (F11). */
public class AffordabilityTool extends AbstractFinanceTool {

    private final AffordabilityAnalyzer analyzer;
    private final Clock clock;

    public AffordabilityTool(AffordabilityAnalyzer analyzer, Clock clock) {
        this.analyzer = analyzer;
        this.clock = clock;
    }

    @Override public String name() { return "affordability"; }

    @Override public String description() {
        return "Checks whether the user can afford a purchase by a date, using cash balances, average monthly "
                + "surplus, savings-goal contributions and a one-month safety buffer. Returns a verdict "
                + "(YES, TIGHT, NO, INSUFFICIENT_DATA), headroom, shortfall and goals that would be affected.";
    }

    @Override public JsonNode schema() {
        return schemaOf("amount:number!::Purchase amount in dollars",
                "date:string::When to buy it, YYYY-MM-DD (default: 3 months from today)");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        LocalDate date;
        try {
            date = args.hasNonNull("date") ? LocalDate.parse(args.get("date").asText())
                    : LocalDate.now(clock).plusMonths(3);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date must be YYYY-MM-DD");
        }
        AffordabilityResult r = analyzer.analyze(money(args, "amount", null), date);
        ObjectNode out = obj();
        out.put("amount", r.amount().toString());
        out.put("date", r.date().toString());
        out.put("verdict", r.verdict().name());
        out.put("current_cash", r.currentCash().toString());
        out.put("average_monthly_surplus", r.forecast().averageSurplus().toString());
        out.put("projected_cash", r.projectedCash().toString());
        out.put("safety_buffer", r.safetyBuffer().toString());
        out.put("headroom", r.headroom().toString());
        out.put("shortfall", r.shortfall().toString());
        var goals = out.putArray("affected_goals");
        r.affectedGoals().forEach(goals::add);
        out.put("months_of_history", r.forecast().monthsOfHistory());
        out.put("low_confidence", r.lowConfidence());
        out.put("summary", r.summary());
        return out;
    }
}
