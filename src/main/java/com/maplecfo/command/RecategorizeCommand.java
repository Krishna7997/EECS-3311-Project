package com.maplecfo.command;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Transaction;
import com.maplecfo.services.TransactionService;

import java.util.NoSuchElementException;
import java.util.Objects;

/** A user's category correction (F02); undo puts back the old category and its source. */
public class RecategorizeCommand implements UndoableCommand {

    private final TransactionService txService;
    private final String txId;
    private final Category newCategory;
    private Category oldCategory;
    private CategorySource oldSource;

    public RecategorizeCommand(TransactionService txService, String txId, Category newCategory) {
        this.txService = Objects.requireNonNull(txService);
        this.txId = Objects.requireNonNull(txId);
        this.newCategory = Objects.requireNonNull(newCategory);
    }

    @Override
    public void execute() {
        Transaction t = txService.findById(txId)
                .orElseThrow(() -> new NoSuchElementException("Transaction " + txId + " no longer exists"));
        oldSource = t.getCategorySource();
        oldCategory = txService.recategorize(txId, newCategory);
    }

    @Override
    public void undo() {
        txService.restoreCategory(txId, oldCategory, oldSource);
    }

    @Override
    public String describe() {
        return "Change category to " + newCategory;
    }
}
