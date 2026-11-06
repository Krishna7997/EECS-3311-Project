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

    /** CRA lets an RRSP go up to $2,000 over the deduction limit without the 1%-a-month tax. */
    public static final Money RRSP_ALLOWANCE = Money.of(2000);

    /** Over the limit far enough that CRA charges tax (an RRSP gets the $2,000 allowance first). */
    public boolean isOverContributed() {
        return accountType == AccountType.RRSP ? available.isLessThan(RRSP_ALLOWANCE.negate()) : available.isNegative();
    }

    /** Over the limit at all, including an RRSP that is still inside its $2,000 allowance. */
    public boolean isOverLimit() {
        return available.isNegative();
    }
}
