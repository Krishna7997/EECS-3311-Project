package com.maplecfo.automation.limits;

/** Result of {@link LimitsService#refreshIfStale}: what happened and a short message for the user (F15). */
public record LimitsStatus(State state, String source, String message) {

    public enum State { UP_TO_DATE, UPDATED, USING_CACHED }
}
