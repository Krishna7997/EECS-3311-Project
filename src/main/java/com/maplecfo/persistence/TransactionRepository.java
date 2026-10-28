package com.maplecfo.persistence;

import com.maplecfo.domain.Transaction;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Stored transactions (F01, F02). */
public interface TransactionRepository extends Repository<Transaction, String> {

    List<Transaction> findByMonth(YearMonth month);

    List<Transaction> findBetween(LocalDate fromInclusive, LocalDate toInclusive);

    boolean existsByFingerprint(String fingerprint);

    /** How many stored transactions have this fingerprint (two identical coffees on one day are both real). */
    int countByFingerprint(String fingerprint);
}
