package com.maplecfo.command;

import com.maplecfo.domain.Budget;
import com.maplecfo.planning.BudgetService;

import java.util.Objects;
import java.util.Optional;

/** Sets a budget; undo restores the budget it replaced, or removes it if there was none. */
public class SetBudgetCommand implements UndoableCommand {

    private final BudgetService budgetService;
    private final Budget newBudget;
    private Optional<Budget> previous = Optional.empty();

    public SetBudgetCommand(BudgetService budgetService, Budget newBudget) {
        this.budgetService = Objects.requireNonNull(budgetService);
        this.newBudget = Objects.requireNonNull(newBudget);
    }

    @Override
    public void execute() {
        previous = budgetService.setBudget(newBudget);
    }

    @Override
    public void undo() {
        if (previous.isPresent()) {
            budgetService.setBudget(previous.get());
        } else {
            budgetService.removeBudget(newBudget.category(), newBudget.month());
        }
    }

    @Override
    public String describe() {
        return "Set " + newBudget.category() + " budget for " + newBudget.month() + " to " + newBudget.limit();
    }
}
