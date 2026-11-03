package com.maplecfo.persistence.memory;

import com.maplecfo.domain.Money;
import com.maplecfo.persistence.NetWorthHistoryRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

public class InMemoryNetWorthHistoryRepository implements NetWorthHistoryRepository {
    private final TreeMap<LocalDate, Money> points = new TreeMap<>();

    @Override
    public void save(LocalDate date, Money total) {
        points.put(date, total);
    }

    @Override
    public Map<LocalDate, Money> findAll() {
        return new TreeMap<>(points);
    }
}
