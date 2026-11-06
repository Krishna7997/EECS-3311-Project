package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.planning.ExtraMoneyPlanner;

/** "Where should extra money go?" (F19): the split is computed in Java; the AI explains it. */
public class ExtraMoneyTool extends AbstractFinanceTool {

    private final ExtraMoneyPlanner planner;

    public ExtraMoneyTool(ExtraMoneyPlanner planner) {
        this.planner = planner;
    }

    @Override public String name() { return "extra_money_plan"; }

    @Override public String description() {
        return "Splits an extra amount of money between paying down debt, an emergency fund and TFSA/RRSP/FHSA room, "
                + "in the order commonly taught in Canadian personal-finance education (high-interest debt, emergency "
                + "fund, FHSA, TFSA, RRSP, low-interest debt). Returns the steps with amounts and reasons, the facts "
                + "used (debts, cash, room) and any amount left over.";
    }

    @Override public JsonNode schema() {
        return schemaOf("amount:number!::The extra amount in dollars");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        ExtraMoneyPlanner.Plan p = planner.plan(money(args, "amount", null));
        ObjectNode out = obj();
        out.put("amount", p.amount().toString());
        var steps = out.putArray("steps");
        for (ExtraMoneyPlanner.Step s : p.steps()) {
            ObjectNode n = steps.addObject();
            n.put("target", s.target());
            n.put("amount", s.amount().toString());
            n.put("reason", s.reason());
        }
        var facts = out.putArray("facts");
        p.facts().forEach(facts::add);
        out.put("left_over", p.leftover().toString());
        out.put("summary", p.summary());
        return out;
    }
}
