package com.maplecfo.importer;

import com.maplecfo.domain.Transaction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Adapter pattern: the "target" interface. The rest of the app only knows
 * this interface; each file format gets an adapter that turns it into
 * {@link Transaction} objects.
 */
public interface TransactionImporter {

    /** Reads the file. Bad rows are skipped and reported by {@link #getRejectedRows()}. */
    List<Transaction> importFile(Path file, String accountId) throws IOException;

    /** Reasons for rows skipped by the last {@link #importFile} call, e.g. "Row 7: invalid date '31/02/2026'". */
    List<String> getRejectedRows();
}
