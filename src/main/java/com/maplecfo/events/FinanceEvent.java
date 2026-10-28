package com.maplecfo.events;

import java.time.Instant;
import java.util.Objects;

/**
 * Something that happened, with optional data (for example the
 * {@code BudgetStatus} that was exceeded).
 */
public record FinanceEvent(EventType type, Object payload, Instant timestamp) {

    public FinanceEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static FinanceEvent of(EventType type, Object payload) {
        return new FinanceEvent(type, payload, Instant.now());
    }

    /** The payload cast to the expected type, or null when it is of another type. */
    public <T> T payloadAs(Class<T> type) {
        return type.isInstance(payload) ? type.cast(payload) : null;
    }
}
