package com.maplecfo.persistence;

import com.maplecfo.domain.Money;

import java.time.LocalDate;
import java.util.Map;

/** One net-worth total per day, for the history chart (F03). */
public interface NetWorthHistoryRepository {

    void save(LocalDate date, Money total);

    /** All points, oldest first. */
    Map<LocalDate, Money> findAll();
}
