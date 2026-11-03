package com.maplecfo.agent;

import com.maplecfo.command.UndoableCommand;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Changes the AI has proposed but the user has not confirmed yet (Stage 1
 * decision D3: the AI can never change data on its own).
 */
public class PendingActionStore {

    private final Map<String, UndoableCommand> pending = new LinkedHashMap<>();
    private final AtomicInteger next = new AtomicInteger(1);

    public synchronized String add(UndoableCommand c) {
        String id = "A" + next.getAndIncrement();
        pending.put(id, c);
        return id;
    }

    /** Removes and returns the action, or empty if it was already confirmed or dismissed. */
    public synchronized Optional<UndoableCommand> take(String actionId) {
        return Optional.ofNullable(pending.remove(actionId == null ? "" : actionId.trim().toUpperCase()));
    }

    public synchronized Map<String, String> list() {
        Map<String, String> out = new LinkedHashMap<>();
        pending.forEach((id, c) -> out.put(id, c.describe()));
        return out;
    }

    public synchronized List<String> ids() {
        return List.copyOf(pending.keySet());
    }
}
