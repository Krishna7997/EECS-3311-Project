package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * Answer to "Can I afford X by date Y?" (F11).
 *
 * @param amount            the purchase amount
 * @param date              when the user wants to buy it
 * @param verdict           YES, TIGHT, NO or INSUFFICIENT_DATA
 * @param currentCash       cash and savings account balances today
 * @param projectedCash     cash expected on the date, after goal contributions
 * @param safetyBuffer      one month of fixed costs kept aside
 * @param headroom          money left after the purchase and the buffer (negative when short)
 * @param shortfall         how much is missing to keep the buffer (zero when YES)
 * @param affectedGoals     goals that would have to be paused to afford it (empty if none)
 * @param forecast          the cash-flow averages used
 */
public record AffordabilityResult(Money amount, LocalDate date, Verdict verdict, Money currentCash,
                                  Money projectedCash, Money safetyBuffer, Money headroom, Money shortfall,
                                  List<String> affectedGoals, CashFlowForecast forecast) {

    public enum Verdict { YES, TIGHT, NO, INSUFFICIENT_DATA }

    public AffordabilityResult {
        affectedGoals = List.copyOf(affectedGoals);
    }

    public boolean lowConfidence() {
        return forecast.lowConfidence();
    }

    /** One paragraph for the CLI, the GUI and the agent. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        switch (verdict) {
            case INSUFFICIENT_DATA -> {
                return "Not enough history to answer: import at least one full month of transactions "
                        + "(two or more for a confident answer).";
            }
            case YES -> sb.append("Yes, ").append(amount).append(" by ").append(date)
                    .append(" looks affordable. You would still have ").append(headroom)
                    .append(" left after keeping a ").append(safetyBuffer).append(" safety buffer.");
            case TIGHT -> sb.append("It is tight: ").append(amount).append(" by ").append(date)
                    .append(" would use ").append(shortfall).append(" of your ").append(safetyBuffer)
                    .append(" safety buffer.");
            case NO -> sb.append("Not yet: by ").append(date).append(" you are projected to have ")
                    .append(projectedCash).append(", so you would be short by ").append(shortfall)
                    .append(" (including a ").append(safetyBuffer).append(" safety buffer).");
        }
        sb.append(" Projection: ").append(currentCash).append(" cash today, about ")
                .append(forecast.cashSurplus()).append(" left over in an average month, ")
                .append(projectedCash).append(" expected by ").append(date).append('.');
        if (!affectedGoals.isEmpty()) {
            sb.append(" It would fit with the full buffer if you paused these goals: ").append(String.join(", ", affectedGoals)).append('.');
        }
        if (lowConfidence()) {
            sb.append(" (Low confidence: less than 2 months of history.)");
        }
        return sb.toString();
    }
}
