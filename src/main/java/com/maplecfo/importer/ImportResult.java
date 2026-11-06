package com.maplecfo.importer;

import com.maplecfo.domain.Money;

import java.util.List;
import java.util.Optional;

/** Summary shown after an import (F01). */
public record ImportResult(String accountId, int imported, int duplicatesSkipped, List<String> rejectedRows,
                           int uncategorized, Money latestBalance, List<String> importedIds) {

    public ImportResult {
        rejectedRows = List.copyOf(rejectedRows);
        importedIds = importedIds == null ? List.of() : List.copyOf(importedIds);
    }

    public ImportResult(String accountId, int imported, int duplicatesSkipped, List<String> rejectedRows,
                        int uncategorized, Money latestBalance) {
        this(accountId, imported, duplicatesSkipped, rejectedRows, uncategorized, latestBalance, List.of());
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
