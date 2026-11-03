package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.agent.PendingActionStore;
import com.maplecfo.command.SetBudgetCommand;
import com.maplecfo.command.UndoableCommand;
import com.maplecfo.domain.Budget;
import com.maplecfo.domain.Category;
import com.maplecfo.planning.BudgetService;

import java.time.Clock;
import java.time.YearMonth;

/**
 * Lets the AI suggest a change without making it: the change is stored as a
 * command in {@link PendingActionStore} and runs only after the user confirms.
 */
public class ProposeActionTool extends AbstractFinanceTool {

    private final PendingActionStore store;
    private final BudgetService budgets;
    private final Clock clock;

    public ProposeActionTool(PendingActionStore store, BudgetService budgets, Clock clock) {
        this.store = store;
        this.budgets = budgets;
        this.clock = clock;
    }

    @Override public String name() { return "propose_action"; }

    @Override public String description() {
        return "Propose a change for the user to confirm (nothing changes until they confirm). Supported: set_budget with category and amount.";
    }

    @Override public JsonNode schema() {
        return schemaOf("action:string!:set_budget:Kind of change",
                "category:string!:GROCERIES|DINING|TRANSPORT|GAS|RENT|UTILITIES|SUBSCRIPTIONS|SHOPPING|TRAVEL|OTHER:Budget category",
                "amount:number!::Monthly limit in dollars",
                "month:string::Month as YYYY-MM (default: this month)");
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        Budget b = new Budget(Category.valueOf(upper(args, "category", "")),
                month(args, "month", YearMonth.now(clock)), money(args, "amount", null));
        UndoableCommand c = new SetBudgetCommand(budgets, b);
        String id = store.add(c);
        ObjectNode out = obj();
        out.put("action_id", id);
        out.put("description", c.describe());
        out.put("summary", "Proposed: " + c.describe() + ". It will only be applied if you confirm it (action " + id + ").");
        return out;
    }
}
