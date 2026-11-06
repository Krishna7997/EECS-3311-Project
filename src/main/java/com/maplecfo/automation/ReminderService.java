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

    static final String CHECKIN_KEY = "checkin.lastDone";

    private final com.maplecfo.persistence.SettingsRepository settings;
    private YearMonth lastCheckinReminder;

    public ReminderService(TransactionService txService, EventBus eventBus, Clock clock) {
        this(txService, eventBus, clock, new com.maplecfo.persistence.memory.InMemorySettingsRepository());
    }

    public ReminderService(TransactionService txService, EventBus eventBus, Clock clock,
                           com.maplecfo.persistence.SettingsRepository settings) {
        this.txService = txService;
        this.eventBus = eventBus;
        this.clock = clock;
        this.settings = settings;
    }

    /**
     * The monthly check-in is due once per calendar month, for users who already have data:
     * import last month's CSVs, update balances, review budgets and registered-account room.
     */
    public boolean isMonthlyCheckinDue() {
        YearMonth now = YearMonth.from(LocalDate.now(clock));
        boolean done = settings.get(CHECKIN_KEY).map(v -> v.equals(now.toString())).orElse(false);
        return !done && !txService.findAll().isEmpty();
    }

    public void completeMonthlyCheckin() {
        settings.put(CHECKIN_KEY, YearMonth.from(LocalDate.now(clock)).toString());
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
        YearMonth now = YearMonth.from(today);
        if (isMonthlyCheckinDue() && !now.equals(lastCheckinReminder)) {
            lastCheckinReminder = now;
            due.add("Time for your " + now.getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                    java.util.Locale.CANADA) + " check-in: import last month's bank CSVs, update your account "
                    + "balances, and look over your budgets and TFSA/RRSP/FHSA room.");
        }
        due.forEach(msg -> eventBus.publish(FinanceEvent.of(EventType.REMINDER_DUE, msg)));
        return due;
    }
}
