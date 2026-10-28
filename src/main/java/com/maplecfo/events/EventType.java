package com.maplecfo.events;

/** Kinds of things that happen in the app that other parts may react to. */
public enum EventType {
    TRANSACTIONS_IMPORTED,
    TRANSACTION_RECATEGORIZED,
    BUDGET_CHANGED,
    BUDGET_WARNING,
    BUDGET_EXCEEDED,
    GOAL_UPDATED,
    CONTRIBUTION_WARNING,
    ACCOUNT_BALANCE_UPDATED,
    CONTRIBUTION_DETECTED,
    LIMITS_UPDATED,
    REMINDER_DUE
}
