package com.maplecfo.domain;

/** Kinds of accounts MapleCFO tracks. */
public enum AccountType {
    CHEQUING(false, false),
    SAVINGS(false, false),
    CREDIT_CARD(true, false),
    TFSA(false, true),
    RRSP(false, true),
    FHSA(false, true),
    NON_REGISTERED(false, false),
    LOAN(true, false);

    private final boolean liability;
    private final boolean registered;

    AccountType(boolean liability, boolean registered) {
        this.liability = liability;
        this.registered = registered;
    }

    /** Credit cards and loans: money owed, counted negatively in net worth. */
    public boolean isLiability() {
        return liability;
    }

    /** TFSA, RRSP and FHSA have CRA contribution limits (F06). */
    public boolean isRegistered() {
        return registered;
    }
}
