package com.maplecfo.events;

import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.planning.ContributionCheck;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns events into user-facing alerts (F04 budget alerts, F06 contribution
 * warnings, F13 reminders). Observer pattern: a concrete observer.
 */
public class AlertService implements FinanceEventListener {

    private final List<Alert> alerts = new ArrayList<>();

    /** Subscribes this service to every event type that produces an alert. */
    public void register(EventBus bus) {
        bus.subscribe(EventType.BUDGET_WARNING, this);
        bus.subscribe(EventType.BUDGET_EXCEEDED, this);
        bus.subscribe(EventType.CONTRIBUTION_WARNING, this);
        bus.subscribe(EventType.REMINDER_DUE, this);
        bus.subscribe(EventType.CONTRIBUTION_DETECTED, this);
    }

    @Override
    public synchronized void onEvent(FinanceEvent e) {
        Alert alert = switch (e.type()) {
            case BUDGET_WARNING, BUDGET_EXCEEDED -> budgetAlert(e);
            case CONTRIBUTION_WARNING -> contributionAlert(e);
            case REMINDER_DUE, CONTRIBUTION_DETECTED ->
                    new Alert(Alert.Severity.INFO, String.valueOf(e.payload()), e.type(), e.timestamp());
            default -> null;
        };
        if (alert != null) {
            alerts.add(alert);
        }
    }

    private Alert budgetAlert(FinanceEvent e) {
        BudgetStatus s = e.payloadAs(BudgetStatus.class);
        boolean exceeded = e.type() == EventType.BUDGET_EXCEEDED;
        String msg;
        if (s == null) {
            msg = exceeded ? "A budget was exceeded" : "A budget is almost used up";
        } else {
            msg = s.budget().category() + (exceeded ? " budget exceeded: " : " budget at "
                    + Math.round(s.percentUsed()) + "%: ") + s.spent() + " spent of " + s.budget().limit();
        }
        return new Alert(exceeded ? Alert.Severity.WARNING : Alert.Severity.INFO, msg, e.type(), e.timestamp());
    }

    private Alert contributionAlert(FinanceEvent e) {
        ContributionCheck c = e.payloadAs(ContributionCheck.class);
        String msg = c == null ? "This contribution would go over your room"
                : c.accountType() + " over-contribution: " + c.amount() + " is " + c.excess()
                + " more than your room. CRA charges 1% per month on the excess.";
        return new Alert(Alert.Severity.CRITICAL, msg, e.type(), e.timestamp());
    }

    public synchronized List<Alert> getActiveAlerts() {
        return List.copyOf(alerts);
    }

    public synchronized void dismissAll() {
        alerts.clear();
    }
}
