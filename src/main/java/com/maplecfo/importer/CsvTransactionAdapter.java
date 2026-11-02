package com.maplecfo.importer;

import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter pattern: the "adapter". Turns one bank's CSV layout, described by a
 * {@link ColumnMapping}, into uniform {@link Transaction} objects. One class
 * handles every bank; only the mapping changes.
 */
public class CsvTransactionAdapter implements TransactionImporter {

    private final ColumnMapping mapping;
    private final List<String> rejectedRows = new ArrayList<>();
    private Money lastBalance;

    public CsvTransactionAdapter(ColumnMapping mapping) {
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    /** The first row of the file (the header, or the first data row of a headerless file). */
    public static List<String> readHeader(Path file) throws IOException {
        List<List<String>> rows = CsvReader.read(file);
        if (rows.isEmpty()) {
            throw new IOException("The file is empty");
        }
        return rows.get(0);
    }

    @Override
    public List<Transaction> importFile(Path file, String accountId) throws IOException {
        rejectedRows.clear();
        lastBalance = null;
        List<List<String>> rows = CsvReader.read(file);
        if (rows.isEmpty()) {
            throw new IOException("The file is empty");
        }
        List<String> header = rows.get(0);
        if (!mapping.isValidFor(header)) {
            throw new MappingMismatchException("Profile '" + mapping.profileName()
                    + "' does not match this file's columns: " + header);
        }
        List<Transaction> result = new ArrayList<>();
        LocalDate latestDate = null;
        int start = mapping.hasHeader() ? 1 : 0;
        for (int i = start; i < rows.size(); i++) {
            int rowNumber = i + 1;
            try {
                Transaction t = parseRow(rows.get(i), header, accountId);
                List<String> problems = validate(t);
                if (!problems.isEmpty()) {
                    rejectedRows.add("Row " + rowNumber + ": " + String.join("; ", problems));
                    continue;
                }
                result.add(t);
                Money balance = parseBalance(rows.get(i), header);
                if (balance != null && (latestDate == null || !t.getDate().isBefore(latestDate))) {
                    latestDate = t.getDate();
                    lastBalance = balance;
                }
            } catch (IllegalArgumentException e) {
                rejectedRows.add("Row " + rowNumber + ": " + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public List<String> getRejectedRows() {
        return List.copyOf(rejectedRows);
    }

    /** The balance on the most recent row, when the file has a balance column (used by auto-sync, F13). */
    public Optional<Money> getLastBalance() {
        return Optional.ofNullable(lastBalance);
    }

    Transaction parseRow(List<String> row, List<String> header, String accountId) {
        String dateText = cell(row, header, mapping.dateColumn());
        LocalDate date;
        try {
            date = LocalDate.parse(dateText, mapping.dateFormatter());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("invalid date '" + dateText + "' (expected " + mapping.dateFormat() + ")");
        }
        String description = cell(row, header, mapping.descriptionColumn());

        BigDecimal amount;
        if (mapping.usesSingleAmount()) {
            amount = parseAmount(cell(row, header, mapping.amountColumn()), "amount");
        } else {
            String debit = cell(row, header, mapping.debitColumn());
            String credit = cell(row, header, mapping.creditColumn());
            if (debit.isEmpty() && credit.isEmpty()) {
                throw new IllegalArgumentException("no debit or credit amount");
            }
            BigDecimal out = debit.isEmpty() ? BigDecimal.ZERO : parseAmount(debit, "debit").abs();
            BigDecimal in = credit.isEmpty() ? BigDecimal.ZERO : parseAmount(credit, "credit").abs();
            amount = in.subtract(out);
        }
        if (mapping.negateAmounts()) {
            amount = amount.negate();
        }
        return new Transaction(UUID.randomUUID().toString(), date, description, null, Money.of(amount), accountId);
    }

    List<String> validate(Transaction t) {
        List<String> problems = new ArrayList<>();
        if (t.getDescription().isBlank()) {
            problems.add("missing description");
        }
        if (t.getAmount().isZero()) {
            problems.add("amount is zero");
        }
        if (t.getDate().isAfter(LocalDate.now().plusDays(1))) {
            problems.add("date " + t.getDate() + " is in the future");
        }
        return problems;
    }

    private Money parseBalance(List<String> row, List<String> header) {
        if (mapping.balanceColumn() == null || mapping.balanceColumn().isBlank()) {
            return null;
        }
        int i = mapping.indexOf(header, mapping.balanceColumn());
        if (i < 0 || i >= row.size() || row.get(i).isBlank()) {
            return null;
        }
        try {
            return Money.of(parseAmount(row.get(i), "balance"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String cell(List<String> row, List<String> header, String column) {
        int i = mapping.indexOf(header, column);
        return i >= 0 && i < row.size() ? row.get(i).trim() : "";
    }

    /** Accepts "1,234.50", "$12.00", "-4.5" and "(4.50)" (accounting negative). */
    static BigDecimal parseAmount(String text, String what) {
        String s = text.trim().replace("$", "").replace(",", "").replace(" ", "");
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1);
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("missing " + what);
        }
        try {
            BigDecimal v = new BigDecimal(s);
            return negative ? v.negate() : v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid " + what + " '" + text + "'");
        }
    }
}
