package com.maplecfo.planning;

import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;

/**
 * Contribution room for one registered account in one year (F06).
 *
 * @param limit     total room created so far (TFSA: all eligible years; FHSA: this year's participation room)
 * @param used      contributions that count against the limit, net of restored withdrawals
 * @param available what can still be contributed without penalty (may be negative when over-contributed)
 */
public record ContributionRoom(AccountType accountType, int year, Money limit, Money used, Money available) {

    public boolean isOverContributed() {
        return available.isNegative();
    }
}
