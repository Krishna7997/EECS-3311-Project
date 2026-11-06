package com.maplecfo.services;

import com.maplecfo.categorization.CategorizationService;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Transaction;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.CsvTransactionAdapter;
import com.maplecfo.importer.ImportResult;
import com.maplecfo.importer.MappingMismatchException;
import com.maplecfo.persistence.MappingProfileRepository;
import com.maplecfo.persistence.TransactionRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * F01 import and F02 corrections. Coordinates profile lookup, the CSV
 * adapter, duplicate detection, categorization, saving and events.
 */
public class TransactionService {

    private final MappingProfileRepository mappings;
    private final CategorizationService categorizer;
    private final TransactionRepository repo;
    private final EventBus eventBus;

    public TransactionService(MappingProfileRepository mappings, CategorizationService categorizer,
                              TransactionRepository repo, EventBus eventBus) {
        this.mappings = Objects.requireNonNull(mappings);
        this.categorizer = Objects.requireNonNull(categorizer);
        this.repo = Objects.requireNonNull(repo);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * Imports a CSV export using a saved mapping profile.
     *
     * @throws MappingMismatchException when the profile is unknown or does not fit the file
     *                                  (the UI then asks the user to map the columns)
     * @throws IOException              when the file cannot be read or is empty (nothing is saved)
     */
    public ImportResult importFrom(Path file, String accountId, String profileName) throws IOException {
        ColumnMapping mapping = mappings.findByName(profileName)
                .orElseThrow(() -> new MappingMismatchException("No mapping profile named '" + profileName + "'"));
        CsvTransactionAdapter adapter = new CsvTransactionAdapter(mapping);
        List<Transaction> parsed = adapter.importFile(file, accountId);

        // Skip rows already stored. Identical rows inside one file are all real
        // (two $2.10 coffees on the same day), so compare counts, not just presence.
        Map<String, Integer> seenInFile = new HashMap<>();
        List<Transaction> fresh = new ArrayList<>();
        int duplicates = 0;
        for (Transaction t : parsed) {
            String fp = t.fingerprint();
            int n = seenInFile.merge(fp, 1, Integer::sum);
            if (n <= repo.countByFingerprint(fp)) {
                duplicates++;
            } else {
                fresh.add(t);
            }
        }

        categorizer.categorizeAll(fresh);
        fresh.forEach(repo::save);
        int uncategorized = (int) fresh.stream().filter(t -> t.getCategory() == Category.UNCATEGORIZED).count();

        ImportResult result = new ImportResult(accountId, fresh.size(), duplicates, adapter.getRejectedRows(),
                uncategorized, adapter.getLastBalance().orElse(null),
                fresh.stream().map(Transaction::getId).toList());
        if (!fresh.isEmpty() || result.latestBalanceIfKnown().isPresent()) {
            eventBus.publish(FinanceEvent.of(EventType.TRANSACTIONS_IMPORTED, result));
        }
        return result;
    }

    /** Finds the saved profile that fits a file's columns (header profiles are tried first). F13. */
    public Optional<ColumnMapping> detectProfile(Path file) throws IOException {
        List<List<String>> rows = com.maplecfo.importer.CsvReader.read(file);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<String> first = rows.get(0);
        return mappings.findAll().stream()
                .sorted(java.util.Comparator.comparing((ColumnMapping m) -> !m.hasHeader()))
                .filter(m -> m.matchesFirstRow(first))
                .findFirst();
    }

    /** Saves a new or edited column-mapping profile. */
    public void saveMappingProfile(ColumnMapping m) {
        mappings.save(m);
    }

    public List<ColumnMapping> getMappingProfiles() {
        return mappings.findAll();
    }

    /**
     * Changes a transaction's category (a user correction) and learns a rule.
     *
     * @return the previous category, kept by {@code RecategorizeCommand} for undo
     * @throws NoSuchElementException when the transaction no longer exists
     */
    public Category recategorize(String txId, Category c) {
        return setCategory(txId, c, CategorySource.USER, true);
    }

    /** Puts back an earlier category without learning a rule (used by undo). */
    public void restoreCategory(String txId, Category c, CategorySource source) {
        setCategory(txId, c, source, false);
    }

    private Category setCategory(String txId, Category c, CategorySource source, boolean learn) {
        Objects.requireNonNull(c, "category");
        Transaction t = repo.findById(txId)
                .orElseThrow(() -> new NoSuchElementException("Transaction " + txId + " no longer exists"));
        Category old = t.getCategory();
        t.setCategory(c, source);
        repo.save(t);
        if (learn) {
            categorizer.learn(t, c);
        }
        eventBus.publish(FinanceEvent.of(EventType.TRANSACTION_RECATEGORIZED, t));
        return old;
    }

    public Optional<Transaction> findById(String txId) {
        return repo.findById(txId);
    }

    public List<Transaction> findByMonth(YearMonth m) {
        return sorted(repo.findByMonth(m));
    }

    public List<Transaction> findAll() {
        return sorted(repo.findAll());
    }

    /** Transactions whose description contains the text (case-insensitive). */
    public List<Transaction> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return sorted(repo.findAll().stream()
                .filter(t -> t.getDescription().toLowerCase().contains(q))
                .toList());
    }

    /** Transactions no rule or model could categorize ("needs review"). */
    public List<Transaction> needsReview() {
        return sorted(repo.findAll().stream().filter(t -> t.getCategory() == Category.UNCATEGORIZED).toList());
    }

    private static List<Transaction> sorted(List<Transaction> txs) {
        List<Transaction> copy = new ArrayList<>(txs);
        copy.sort(Comparator.comparing(Transaction::getDate).reversed());
        return copy;
    }
}
