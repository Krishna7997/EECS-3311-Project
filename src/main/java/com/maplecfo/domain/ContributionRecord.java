package com.maplecfo.domain;

import java.time.LocalDate;
import java.util.Objects;

/** A logged TFSA/RRSP/FHSA contribution or withdrawal (F06, F14). Amount is always positive. */
public record ContributionRecord(AccountType accountType, LocalDate date, Money amount, ContributionKind kind) {

    public ContributionRecord {
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(kind, "kind");
        if (!accountType.isRegistered()) {
            throw new IllegalArgumentException(accountType + " is not a registered account");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }
}
