package com.maplecfo.planning;

import com.maplecfo.domain.Money;
import com.maplecfo.domain.SavingsGoal;

import java.time.LocalDate;

/**
 * Whether a savings goal will be reached in time (F09).
 *
 * @param goal                the goal
 * @param monthsLeft          whole months until the deadline (0 when there is no deadline or it has passed)
 * @param requiredMonthly     amount to save each month to meet the deadline (zero when there is no deadline)
 * @param availableMonthly    average monthly surplus left after the other goals' monthly needs
 * @param projectedCompletion when the goal is reached at the available rate; null if it never is
 * @param onTrack             true when the available amount covers the required amount
 * @param shortfallMonthly    how much more per month is needed (zero when on track)
 * @param lowConfidence       true when the forecast is based on fewer than 2 months of history
 */
public record GoalForecast(SavingsGoal goal, long monthsLeft, Money requiredMonthly, Money availableMonthly,
                           LocalDate projectedCompletion, boolean onTrack, Money shortfallMonthly,
                           boolean lowConfidence) {

    public boolean isComplete() {
        return goal.isComplete();
    }

    /** One sentence for the CLI, the GUI and the agent. */
    public String summary() {
        if (goal.isComplete()) {
            return goal.getName() + " is complete: " + goal.getSaved() + " of " + goal.getTarget() + " saved.";
        }
        StringBuilder sb = new StringBuilder(goal.getName()).append(": ").append(goal.getSaved()).append(" of ")
                .append(goal.getTarget()).append(" saved, ").append(goal.remaining()).append(" to go. ");
        if (goal.getDeadline() != null) {
            sb.append("Needs ").append(requiredMonthly).append(" a month to reach it by ").append(goal.getDeadline())
                    .append("; you have about ").append(availableMonthly).append(" a month available. ");
            sb.append(onTrack ? "On track." : "Not on track: short by " + shortfallMonthly + " a month.");
        } else {
            sb.append("No deadline; about ").append(availableMonthly).append(" a month is available.");
        }
        if (projectedCompletion != null) {
            sb.append(" Projected completion: ").append(projectedCompletion).append('.');
        }
        if (lowConfidence) {
            sb.append(" (Low confidence: less than 2 months of history.)");
        }
        return sb.toString();
    }
}
