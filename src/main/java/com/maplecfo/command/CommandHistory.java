package com.maplecfo.command;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Command pattern: the "invoker". Runs commands and keeps undo and redo stacks.
 * If undo or redo fails (for example the item was deleted in the meantime),
 * the stacks are left exactly as they were and the failure is reported.
 */
public class CommandHistory {

    public static final int MAX_HISTORY = 100;

    private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
    private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();

    public synchronized void run(UndoableCommand c) {
        Objects.requireNonNull(c, "command");
        c.execute();
        undoStack.push(c);
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    /** @return false when there is nothing to undo or the undo failed */
    public synchronized boolean undo() {
        UndoableCommand c = undoStack.peek();
        if (c == null) {
            return false;
        }
        try {
            c.undo();
        } catch (RuntimeException e) {
            return false;
        }
        undoStack.pop();
        redoStack.push(c);
        return true;
    }

    /** @return false when there is nothing to redo or the redo failed */
    public synchronized boolean redo() {
        UndoableCommand c = redoStack.peek();
        if (c == null) {
            return false;
        }
        try {
            c.execute();
        } catch (RuntimeException e) {
            return false;
        }
        redoStack.pop();
        undoStack.push(c);
        return true;
    }

    public synchronized boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public synchronized Optional<String> nextUndoDescription() {
        return Optional.ofNullable(undoStack.peek()).map(UndoableCommand::describe);
    }

    public synchronized Optional<String> nextRedoDescription() {
        return Optional.ofNullable(redoStack.peek()).map(UndoableCommand::describe);
    }
}
