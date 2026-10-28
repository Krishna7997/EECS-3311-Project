package com.maplecfo.command;

/**
 * Command pattern: the "command" role. Every user edit (and every action the
 * AI proposes) is wrapped in a command, so it can be undone and redone.
 */
public interface UndoableCommand {

    void execute();

    void undo();

    /** Short text for the undo button and the history, e.g. "Set DINING budget to $250.00". */
    String describe();
}
