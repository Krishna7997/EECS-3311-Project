package com.maplecfo.planning;

import com.maplecfo.domain.Debt;

import java.util.List;

/**
 * Strategy pattern: the "strategy" role. Decides which debt receives the
 * extra money first. {@link DebtPayoffPlanner} (the context) runs the same
 * month-by-month simulation for any strategy.
 */
public interface PayoffStrategy {

    /** Returns a new list in payoff order; the input list is not modified. */
    List<Debt> orderDebts(List<Debt> debts);

    String name();
}
