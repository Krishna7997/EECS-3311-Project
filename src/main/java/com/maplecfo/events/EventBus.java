package com.maplecfo.events;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer pattern: the "subject" role. Services publish events here instead
 * of calling each other, so for example the importer does not need to know
 * that budgets, alerts and the dashboard react to new transactions.
 *
 * <p>A listener that throws does not stop the other listeners; its error is
 * kept in {@link #getListenerErrors()} so it can be shown or logged.
 */
public class EventBus {

    private final Map<EventType, List<FinanceEventListener>> listeners = new EnumMap<>(EventType.class);
    private final List<String> listenerErrors = new CopyOnWriteArrayList<>();

    public synchronized void subscribe(EventType t, FinanceEventListener l) {
        Objects.requireNonNull(t, "type");
        Objects.requireNonNull(l, "listener");
        List<FinanceEventListener> list = listeners.computeIfAbsent(t, k -> new CopyOnWriteArrayList<>());
        if (!list.contains(l)) {
            list.add(l);
        }
    }

    public synchronized void unsubscribe(EventType t, FinanceEventListener l) {
        List<FinanceEventListener> list = listeners.get(t);
        if (list != null) {
            list.remove(l);
        }
    }

    public void publish(FinanceEvent e) {
        Objects.requireNonNull(e, "event");
        List<FinanceEventListener> targets;
        synchronized (this) {
            targets = new ArrayList<>(listeners.getOrDefault(e.type(), List.of()));
        }
        for (FinanceEventListener l : targets) {
            try {
                l.onEvent(e);
            } catch (RuntimeException ex) {
                listenerErrors.add(e.type() + " -> " + l.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    public List<String> getListenerErrors() {
        return List.copyOf(listenerErrors);
    }
}
