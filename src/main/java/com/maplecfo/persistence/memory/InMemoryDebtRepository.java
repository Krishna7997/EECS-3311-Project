package com.maplecfo.persistence.memory;

import com.maplecfo.domain.Debt;
import com.maplecfo.persistence.DebtRepository;

public class InMemoryDebtRepository extends InMemoryRepository<Debt, String> implements DebtRepository {
    public InMemoryDebtRepository() {
        super(Debt::getId);
    }
}
