package com.maplecfo.events;

import java.time.Instant;

/** A message shown to the user, e.g. "Dining budget exceeded: $230.00 of $200.00". */
public record Alert(Severity severity, String message, EventType source, Instant createdAt) {

    public enum Severity { INFO, WARNING, CRITICAL }
}
