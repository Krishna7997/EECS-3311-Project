package com.maplecfo.planning;

import com.maplecfo.domain.Debt;

import java.util.Comparator;
import java.util.List;

/** Smallest balance first: quick wins that keep people motivated. Ties go to the higher rate. */
public class SnowballStrategy implements PayoffStrategy {

    @Override
    public List<Debt> orderDebts(List<Debt> debts) {
        return debts.stream()
                .sorted(Comparator.comparing(Debt::getBalance)
                        .thenComparing(Comparator.comparingDouble(Debt::getApr).reversed()))
                .toList();
    }

    @Override
    public String name() {
        return "Snowball";
    }
}
