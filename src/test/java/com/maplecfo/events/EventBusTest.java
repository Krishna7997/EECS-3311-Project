package com.maplecfo.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    @Test
    void deliversOnlyToSubscribersOfThatType() {
        EventBus bus = new EventBus();
        List<EventType> seen = new ArrayList<>();
        bus.subscribe(EventType.BUDGET_EXCEEDED, e -> seen.add(e.type()));
        bus.publish(FinanceEvent.of(EventType.GOAL_UPDATED, null));
        bus.publish(FinanceEvent.of(EventType.BUDGET_EXCEEDED, null));
        assertEquals(List.of(EventType.BUDGET_EXCEEDED), seen);
    }

    @Test
    void unsubscribeStopsDelivery() {
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        FinanceEventListener l = e -> seen.add("x");
        bus.subscribe(EventType.REMINDER_DUE, l);
        bus.subscribe(EventType.REMINDER_DUE, l); // duplicate subscription is ignored
        bus.publish(FinanceEvent.of(EventType.REMINDER_DUE, null));
        bus.unsubscribe(EventType.REMINDER_DUE, l);
        bus.publish(FinanceEvent.of(EventType.REMINDER_DUE, null));
        assertEquals(1, seen.size());
    }

    @Test
    void failingListenerDoesNotStopOthers() {
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(EventType.LIMITS_UPDATED, e -> { throw new IllegalStateException("boom"); });
        bus.subscribe(EventType.LIMITS_UPDATED, e -> seen.add("second"));
        bus.publish(FinanceEvent.of(EventType.LIMITS_UPDATED, null));
        assertEquals(List.of("second"), seen);
        assertEquals(1, bus.getListenerErrors().size());
    }

    @Test
    void payloadAsChecksTheType() {
        FinanceEvent e = FinanceEvent.of(EventType.REMINDER_DUE, "hello");
        assertEquals("hello", e.payloadAs(String.class));
        assertNull(e.payloadAs(Integer.class));
    }
}
