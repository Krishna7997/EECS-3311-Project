package com.maplecfo.importer;

import com.maplecfo.domain.Money;

import java.util.List;
import java.util.Optional;

/** Summary shown after an import (F01). */
public record ImportResult(String accountId, int imported, int duplicatesSkipped, List<String> rejectedRows,
                           int uncategorized, Money latestBalance) {

    public ImportResult {
        rejectedRows = List.copyOf(rejectedRows);
    }

    public Optional<Money> latestBalanceIfKnown() {
        return Optional.ofNullable(latestBalance);
    }

    @Override
    public String toString() {
        return imported + " imported, " + duplicatesSkipped + " duplicates skipped, "
                + rejectedRows.size() + " rejected, " + uncategorized + " need review";
    }
}
