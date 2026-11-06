package com.maplecfo.automation;

import com.maplecfo.domain.Transaction;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.services.TransactionService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * F13 reminders, checked when the app starts (and on demand): "no import in 30 days" and
 * "last month's summary is ready". Each reminder is raised once, as a REMINDER_DUE event.
 */
public class ReminderService {

    static final int STALE_DAYS = 30;

    private final TransactionService txService;
    private final EventBus eventBus;
    private final Clock clock;
    private String lastImportReminder;
    private YearMonth lastSummaryReminder;

    public ReminderService(TransactionService txService, EventBus eventBus, Clock clock) {
        this.txService = txService;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    /** Publishes any reminders that are due and returns their text. */
    public List<String> checkDue() {
        List<String> due = new ArrayList<>();
        LocalDate today = LocalDate.now(clock);
        Optional<LocalDate> latest = txService.findAll().stream().map(Transaction::getDate)
                .max(LocalDate::compareTo);
        if (latest.isEmpty()) {
            return due;
        }
        long days = ChronoUnit.DAYS.between(latest.get(), today);
        if (days >= STALE_DAYS) {
            String key = latest.get().toString();
            if (!key.equals(lastImportReminder)) {
                lastImportReminder = key;
                due.add("No new transactions in " + days + " days. Download a CSV from your bank and import it "
                        + "(or drop it in your import folder).");
            }
        }
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        boolean hasLastMonth = txService.findByMonth(lastMonth).size() > 0;
        if (hasLastMonth && !lastMonth.equals(lastSummaryReminder)) {
            lastSummaryReminder = lastMonth;
            due.add("Your " + lastMonth.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.CANADA)
                    + " summary is ready: open Monthly Report or run `report`.");
        }
        due.forEach(msg -> eventBus.publish(FinanceEvent.of(EventType.REMINDER_DUE, msg)));
        return due;
    }
}
