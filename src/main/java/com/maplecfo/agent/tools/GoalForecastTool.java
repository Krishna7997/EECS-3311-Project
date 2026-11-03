package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.planning.GoalForecast;
import com.maplecfo.planning.GoalService;

import java.util.List;

/** Savings goals and whether each one is on track (F09). */
public class GoalForecastTool extends AbstractFinanceTool {

    private final GoalService goals;

    public GoalForecastTool(GoalService goals) {
        this.goals = goals;
    }

    @Override public String name() { return "goal_forecast"; }

    @Override public String description() {
        return "The user's savings goals: target, saved, deadline, the monthly amount needed, the monthly "
                + "amount available from their average surplus, projected completion date and whether it is on track.";
    }

    @Override public JsonNode schema() {
        return schemaOf("goal:string::Goal name or id (default: all goals)");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        String wanted = args.hasNonNull("goal") ? args.get("goal").asText().trim() : "";
        List<GoalForecast> all = goals.forecastAll().stream()
                .filter(f -> wanted.isEmpty() || f.goal().getId().equalsIgnoreCase(wanted)
                        || f.goal().getName().toLowerCase().contains(wanted.toLowerCase()))
                .toList();
        ObjectNode out = obj();
        ArrayNode list = out.putArray("goals");
        StringBuilder summary = new StringBuilder();
        for (GoalForecast f : all) {
            ObjectNode g = list.addObject();
            g.put("id", f.goal().getId());
            g.put("name", f.goal().getName());
            g.put("target", f.goal().getTarget().toString());
            g.put("saved", f.goal().getSaved().toString());
            g.put("remaining", f.goal().remaining().toString());
            g.put("deadline", f.goal().getDeadline() == null ? "none" : f.goal().getDeadline().toString());
            g.put("required_monthly", f.requiredMonthly().toString());
            g.put("available_monthly", f.availableMonthly().toString());
            g.put("projected_completion", f.projectedCompletion() == null ? "unknown"
                    : f.projectedCompletion().toString());
            g.put("on_track", f.onTrack());
            g.put("shortfall_monthly", f.shortfallMonthly().toString());
            g.put("complete", f.isComplete());
            g.put("low_confidence", f.lowConfidence());
            summary.append(f.summary()).append(' ');
        }
        out.put("summary", all.isEmpty()
                ? (wanted.isEmpty() ? "The user has no savings goals yet." : "No goal matches '" + wanted + "'.")
                : summary.toString().trim());
        return out;
    }
}
