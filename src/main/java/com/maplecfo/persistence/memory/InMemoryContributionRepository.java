package com.maplecfo.persistence.memory;

import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.persistence.ContributionRepository;

import java.util.ArrayList;
import java.util.List;

public class InMemoryContributionRepository implements ContributionRepository {

    private final List<ContributionRecord> records = new ArrayList<>();

    @Override
    public void add(ContributionRecord record) {
        records.add(record);
    }

    @Override
    public void remove(ContributionRecord record) {
        records.remove(record);
    }

    @Override
    public List<ContributionRecord> findByType(AccountType type) {
        return records.stream().filter(r -> r.accountType() == type).toList();
    }
}
