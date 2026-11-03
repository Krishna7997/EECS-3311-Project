package com.maplecfo.planning;

import com.maplecfo.domain.Money;

import java.time.LocalDate;
import java.util.Map;

/** Net worth on one day (F03): total, assets, liabilities and a subtotal per group. */
public record NetWorthSnapshot(LocalDate date, Money total, Money assets, Money liabilities, Map<String, Money> groups) {

    public NetWorthSnapshot {
        groups = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(groups)); // keeps display order
    }
}
