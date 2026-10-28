package com.maplecfo.persistence;

import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionRecord;

import java.util.List;

/** Logged TFSA/RRSP/FHSA contributions and withdrawals (F06, F14). */
public interface ContributionRepository {

    void add(ContributionRecord record);

    /** Removes one matching record (used by undo). */
    void remove(ContributionRecord record);

    List<ContributionRecord> findByType(AccountType type);
}
