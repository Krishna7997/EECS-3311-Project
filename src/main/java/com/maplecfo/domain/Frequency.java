package com.maplecfo.domain;

/** How often a recurring charge repeats (F05). */
public enum Frequency {
    WEEKLY(7),
    MONTHLY(30),
    YEARLY(365);

    private final int approxDays;

    Frequency(int approxDays) {
        this.approxDays = approxDays;
    }

    public int approxDays() {
        return approxDays;
    }
}
