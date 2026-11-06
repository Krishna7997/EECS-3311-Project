package com.maplecfo.domain;

/** Money put into or taken out of a registered account. */
public enum ContributionKind {
    /** Normal contribution: uses room. */
    CONTRIBUTION("Contribution"),
    /** Normal withdrawal. TFSA: room comes back next January. RRSP/FHSA: room does not come back. */
    WITHDRAWAL("Withdrawal"),
    /** RRSP Home Buyers' Plan withdrawal: tax-free, must be paid back over 15 years. */
    HBP_WITHDRAWAL("Home Buyers' Plan withdrawal"),
    /** RRSP Lifelong Learning Plan withdrawal: tax-free, must be paid back over 10 years. */
    LLP_WITHDRAWAL("Lifelong Learning Plan withdrawal"),
    /** RRSP contribution designated as a Home Buyers' Plan repayment: does not use room. */
    HBP_REPAYMENT("Home Buyers' Plan repayment"),
    /** RRSP contribution designated as a Lifelong Learning Plan repayment: does not use room. */
    LLP_REPAYMENT("Lifelong Learning Plan repayment"),
    /** FHSA money moved to an RRSP: tax-free and does not use RRSP room. */
    TRANSFER_TO_RRSP("Transfer to RRSP");

    private final String label;

    ContributionKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Money leaving the registered account. */
    public boolean isWithdrawal() {
        return this == WITHDRAWAL || this == HBP_WITHDRAWAL || this == LLP_WITHDRAWAL || this == TRANSFER_TO_RRSP;
    }

    /** Money going in (a contribution or a repayment). */
    public boolean isDeposit() {
        return !isWithdrawal();
    }

    /** Which accounts this kind can be used with. */
    public boolean allowedFor(AccountType type) {
        return switch (this) {
            case CONTRIBUTION, WITHDRAWAL -> type.isRegistered();
            case HBP_WITHDRAWAL, LLP_WITHDRAWAL, HBP_REPAYMENT, LLP_REPAYMENT -> type == AccountType.RRSP;
            case TRANSFER_TO_RRSP -> type == AccountType.FHSA;
        };
    }
}
