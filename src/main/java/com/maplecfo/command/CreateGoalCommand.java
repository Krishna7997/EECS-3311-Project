package com.maplecfo.command;

import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.planning.GoalService;

import java.util.Objects;

/** Creates a savings goal (F09); undo deletes it again. */
public class CreateGoalCommand implements UndoableCommand {

    private final GoalService goals;
    private final SavingsGoal goal;

    public CreateGoalCommand(GoalService goals, SavingsGoal goal) {
        this.goals = Objects.requireNonNull(goals);
        this.goal = Objects.requireNonNull(goal);
    }

    @Override
    public void execute() {
        goals.createGoal(goal);
    }

    @Override
    public void undo() {
        goals.deleteGoal(goal.getId());
    }

    @Override
    public String describe() {
        return "Create goal " + goal.getName() + " (" + goal.getTarget()
                + (goal.getDeadline() != null ? " by " + goal.getDeadline() : "") + ")";
    }
}
