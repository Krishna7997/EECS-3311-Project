package com.maplecfo.planning;

import com.maplecfo.domain.Budget;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.events.FinanceEventListener;
import com.maplecfo.persistence.BudgetRepository;
import com.maplecfo.persistence.TransactionRepository;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * F04 monthly budgets and overspending alerts.
 * Observer pattern: a concrete observer. It listens for new or recategorized
 * transactions, recomputes every budget and publishes BUDGET_WARNING (80%)
 * or BUDGET_EXCEEDED (100%) once per budget and threshold.
 */
public class BudgetService implements FinanceEventListener {

    private final BudgetRepository budgetRepo;
    private final TransactionRepository txRepo;
    private final EventBus eventBus;
    /** "DINING|2026-10|100" once that alert was sent, so it is not repeated. */
    private final Set<String> alertsSent = new HashSet<>();

    public BudgetService(BudgetRepository budgetRepo, TransactionRepository txRepo, EventBus eventBus) {
        this.budgetRepo = Objects.requireNonNull(budgetRepo);
        this.txRepo = Objects.requireNonNull(txRepo);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /** Subscribes to the events that can change how much was spent. */
    public void register() {
        eventBus.subscribe(EventType.TRANSACTIONS_IMPORTED, this);
        eventBus.subscribe(EventType.TRANSACTION_RECATEGORIZED, this);
    }

    /**
     * Saves a budget, replacing any budget for the same category and month.
     *
     * @return the budget that was replaced, so {@code SetBudgetCommand} can restore it on undo
     */
    public Optional<Budget> setBudget(Budget b) {
        Objects.requireNonNull(b, "budget");
        Optional<Budget> previous = budgetRepo.find(b.category(), b.month());
        budgetRepo.save(b);
        clearAlerts(b.category(), b.month());
        eventBus.publish(FinanceEvent.of(EventType.BUDGET_CHANGED, b));
        checkMonth(b.month());
        return previous;
    }

    public void removeBudget(Category c, YearMonth m) {
        budgetRepo.delete(c, m);
        clearAlerts(c, m);
        eventBus.publish(FinanceEvent.of(EventType.BUDGET_CHANGED, null));
    }

    public Optional<Budget> getBudget(Category c, YearMonth m) {
        return budgetRepo.find(c, m);
    }

    /** Spending against every budget of the month. No transactions yet means 0% used. */
    public List<BudgetStatus> getStatus(YearMonth m) {
        List<Transaction> txs = txRepo.findByMonth(m);
        List<BudgetStatus> result = new ArrayList<>();
        for (Budget b : budgetRepo.findByMonth(m)) {
            result.add(new BudgetStatus(b, spentIn(txs, b.category())));
        }
        return result;
    }

    @Override
    public void onEvent(FinanceEvent e) {
        if (e.type() == EventType.TRANSACTIONS_IMPORTED || e.type() == EventType.TRANSACTION_RECATEGORIZED) {
            Set<YearMonth> months = new HashSet<>();
            for (Transaction t : txRepo.findAll()) {
                months.add(YearMonth.from(t.getDate()));
            }
            months.forEach(this::checkMonth);
        }
    }

    private void checkMonth(YearMonth m) {
        for (BudgetStatus s : getStatus(m)) {
            if (s.isExceeded()) {
                sendOnce(s, "100", EventType.BUDGET_EXCEEDED);
            } else if (s.isNearLimit()) {
                sendOnce(s, "80", EventType.BUDGET_WARNING);
            }
        }
    }

    private void sendOnce(BudgetStatus s, String level, EventType type) {
        String key = s.budget().category() + "|" + s.budget().month() + "|" + level;
        if (alertsSent.add(key)) {
            eventBus.publish(FinanceEvent.of(type, s));
        }
    }

    private void clearAlerts(Category c, YearMonth m) {
        alertsSent.removeIf(k -> k.startsWith(c + "|" + m + "|"));
    }

    /** Expenses are negative and refunds positive, so spending is minus their sum (never below zero). */
    static Money spentIn(List<Transaction> monthTxs, Category category) {
        Money net = Money.zero();
        for (Transaction t : monthTxs) {
            if (t.getCategory() == category) {
                net = net.plus(t.getAmount());
            }
        }
        return net.negate().max(Money.zero());
    }
}
