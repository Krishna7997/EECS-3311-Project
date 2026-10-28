package com.maplecfo.persistence.memory;

import com.maplecfo.domain.Transaction;
import com.maplecfo.persistence.TransactionRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class InMemoryTransactionRepository extends InMemoryRepository<Transaction, String>
        implements TransactionRepository {

    public InMemoryTransactionRepository() {
        super(Transaction::getId);
    }

    @Override
    public List<Transaction> findByMonth(YearMonth month) {
        return findAll().stream().filter(t -> YearMonth.from(t.getDate()).equals(month)).toList();
    }

    @Override
    public List<Transaction> findBetween(LocalDate from, LocalDate to) {
        return findAll().stream()
                .filter(t -> !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .toList();
    }

    @Override
    public boolean existsByFingerprint(String fingerprint) {
        return countByFingerprint(fingerprint) > 0;
    }

    @Override
    public int countByFingerprint(String fingerprint) {
        return (int) findAll().stream().filter(t -> t.fingerprint().equals(fingerprint)).count();
    }
}
