package com.maplecfo.command;

import com.maplecfo.domain.Budget;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.events.EventBus;
import com.maplecfo.persistence.memory.InMemoryBudgetRepository;
import com.maplecfo.persistence.memory.InMemoryTransactionRepository;
import com.maplecfo.planning.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandHistoryTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private BudgetService budgets;
    private CommandHistory history;

    @BeforeEach
    void setUp() {
        budgets = new BudgetService(new InMemoryBudgetRepository(), new InMemoryTransactionRepository(), new EventBus());
        history = new CommandHistory();
    }

    private Money limit() {
        return budgets.getBudget(Category.DINING, OCT).map(Budget::limit).orElse(null);
    }

    @Test
    void undoRemovesANewBudgetAndRedoBringsItBack() {
        history.run(new SetBudgetCommand(budgets, new Budget(Category.DINING, OCT, Money.of(200))));
        assertEquals(Money.of(200), limit());
        assertTrue(history.undo());
        assertNull(limit());
        assertTrue(history.redo());
        assertEquals(Money.of(200), limit());
    }

    @Test
    void undoRestoresTheReplacedBudget() {
        history.run(new SetBudgetCommand(budgets, new Budget(Category.DINING, OCT, Money.of(200))));
        history.run(new SetBudgetCommand(budgets, new Budget(Category.DINING, OCT, Money.of(250))));
        assertEquals("Set DINING budget for 2026-10 to $250.00", history.nextUndoDescription().orElseThrow());
        history.undo();
        assertEquals(Money.of(200), limit());
    }

    @Test
    void newCommandClearsRedo() {
        history.run(new SetBudgetCommand(budgets, new Budget(Category.DINING, OCT, Money.of(200))));
        history.undo();
        assertTrue(history.canRedo());
        history.run(new SetBudgetCommand(budgets, new Budget(Category.TRAVEL, OCT, Money.of(500))));
        assertFalse(history.canRedo());
    }

    @Test
    void nothingToUndoOrRedo() {
        assertFalse(history.undo());
        assertFalse(history.redo());
    }

    @Test
    void failedUndoLeavesStacksUnchanged() {
        List<String> log = new ArrayList<>();
        UndoableCommand fragile = new UndoableCommand() {
            public void execute() { log.add("do"); }
            public void undo() { throw new IllegalStateException("item was deleted"); }
            public String describe() { return "fragile"; }
        };
        history.run(fragile);
        assertFalse(history.undo());
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
    }
}
