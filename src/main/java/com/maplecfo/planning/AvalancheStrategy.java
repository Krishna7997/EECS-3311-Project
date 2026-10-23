package com.maplecfo.planning;

import com.maplecfo.domain.Debt;

import java.util.Comparator;
import java.util.List;

/** Highest interest rate first: the cheapest way to pay off debt. Ties go to the smaller balance. */
public class AvalancheStrategy implements PayoffStrategy {

    @Override
    public List<Debt> orderDebts(List<Debt> debts) {
        return debts.stream()
                .sorted(Comparator.comparingDouble(Debt::getApr).reversed()
                        .thenComparing(Debt::getBalance))
                .toList();
    }

    @Override
    public String name() {
        return "Avalanche";
    }
}
