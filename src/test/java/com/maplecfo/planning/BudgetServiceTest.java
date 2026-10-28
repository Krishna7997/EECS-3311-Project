package com.maplecfo.planning;

import com.maplecfo.domain.Budget;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.events.Alert;
import com.maplecfo.events.AlertService;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.persistence.memory.InMemoryBudgetRepository;
import com.maplecfo.persistence.memory.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BudgetServiceTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private InMemoryTransactionRepository txs;
    private EventBus bus;
    private AlertService alerts;
    private BudgetService service;
    private int ids;

    @BeforeEach
    void setUp() {
        txs = new InMemoryTransactionRepository();
        bus = new EventBus();
        alerts = new AlertService();
        alerts.register(bus);
        service = new BudgetService(new InMemoryBudgetRepository(), txs, bus);
        service.register();
    }

    private void spend(String date, String amount, Category c) {
        Transaction t = new Transaction("t" + (ids++), LocalDate.parse(date), "x", "x", Money.of(amount), "acc");
        t.setCategory(c, CategorySource.RULE);
        txs.save(t);
    }

    @Test
    void noTransactionsMeansZeroPercent() {
        service.setBudget(new Budget(Category.DINING, OCT, Money.of(200)));
        BudgetStatus s = service.getStatus(OCT).get(0);
        assertEquals(Money.zero(), s.spent());
        assertEquals(0.0, s.percentUsed(), 1e-9);
    }

    @Test
    void spendingCountsOnlyThatCategoryAndMonthAndRefundsReduceIt() {
        service.setBudget(new Budget(Category.DINING, OCT, Money.of(200)));
        spend("2026-10-02", "-50.00", Category.DINING);
        spend("2026-10-09", "-30.00", Category.DINING);
        spend("2026-10-10", "10.00", Category.DINING);      // refund
        spend("2026-10-11", "-99.00", Category.GROCERIES);  // other category
        spend("2026-09-30", "-80.00", Category.DINING);     // other month
        assertEquals(Money.of(70), service.getStatus(OCT).get(0).spent());
    }

    @Test
    void importPushingOverTheLimitRaisesOneAlert() {
        service.setBudget(new Budget(Category.DINING, OCT, Money.of(100)));
        spend("2026-10-02", "-120.00", Category.DINING);
        bus.publish(FinanceEvent.of(EventType.TRANSACTIONS_IMPORTED, null));
        bus.publish(FinanceEvent.of(EventType.TRANSACTIONS_IMPORTED, null)); // no duplicate alert
        List<Alert> active = alerts.getActiveAlerts();
        assertEquals(1, active.size());
        assertEquals(EventType.BUDGET_EXCEEDED, active.get(0).source());
        assertTrue(active.get(0).message().contains("DINING budget exceeded"));
    }

    @Test
    void eightyPercentGivesAWarning() {
        service.setBudget(new Budget(Category.GROCERIES, OCT, Money.of(100)));
        spend("2026-10-05", "-85.00", Category.GROCERIES);
        bus.publish(FinanceEvent.of(EventType.TRANSACTIONS_IMPORTED, null));
        assertEquals(EventType.BUDGET_WARNING, alerts.getActiveAlerts().get(0).source());
    }

    @Test
    void replacingABudgetReturnsTheOldOne() {
        Budget first = new Budget(Category.DINING, OCT, Money.of(200));
        assertEquals(Optional.empty(), service.setBudget(first));
        Optional<Budget> previous = service.setBudget(new Budget(Category.DINING, OCT, Money.of(250)));
        assertEquals(Optional.of(first), previous);
        assertEquals(Money.of(250), service.getBudget(Category.DINING, OCT).orElseThrow().limit());
    }

    @Test
    void removeBudget() {
        service.setBudget(new Budget(Category.DINING, OCT, Money.of(200)));
        service.removeBudget(Category.DINING, OCT);
        assertTrue(service.getStatus(OCT).isEmpty());
    }
}
