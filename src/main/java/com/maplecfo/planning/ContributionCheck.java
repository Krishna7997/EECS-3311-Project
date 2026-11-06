package com.maplecfo.planning;

import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;

import java.util.List;

/**
 * Answer to "can I contribute this amount?" (F06).
 * OK: fits in the room. OVER: exceeds it by {@code excess}.
 * UNKNOWN: the profile is missing fields (listed) or limits are unavailable.
 */
public record ContributionCheck(Status status, AccountType accountType, Money amount,
                                ContributionRoom room, Money excess, List<String> missingFields) {

    /**
     * OK: fits. ALLOWANCE: RRSP only, over the limit but within the $2,000 CRA allows without tax.
     * OVER: over the limit (for an RRSP, by more than $2,000), CRA charges 1% a month. UNKNOWN: profile incomplete.
     */
    public enum Status { OK, ALLOWANCE, OVER, UNKNOWN }

    public ContributionCheck {
        missingFields = List.copyOf(missingFields);
    }

    static ContributionCheck unknown(AccountType type, Money amount, List<String> missing) {
        return new ContributionCheck(Status.UNKNOWN, type, amount, null, Money.zero(), missing);
    }

    /** Room left after this contribution (only meaningful for OK). */
    public Money roomAfter() {
        return room == null ? Money.zero() : room.available().minus(amount);
    }
}
