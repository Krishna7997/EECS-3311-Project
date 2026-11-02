package com.maplecfo.importer;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A saved "column mapping profile" (F01) that says how to read one bank's
 * CSV layout. Columns are named by their header text or, for files without
 * a header row, by their 1-based position ("1", "2", ...).
 *
 * <p>The amount comes either from one signed {@code amountColumn}, or from
 * separate {@code debitColumn} (money out) and {@code creditColumn} (money in).
 * {@code negateAmounts} flips the sign for cards that export purchases as positive numbers.
 */
public record ColumnMapping(String profileName, boolean hasHeader, String dateColumn, String dateFormat,
                            String descriptionColumn, String amountColumn, String debitColumn,
                            String creditColumn, boolean negateAmounts, String balanceColumn) {

    public ColumnMapping {
        if (profileName == null || profileName.isBlank()) {
            throw new IllegalArgumentException("Profile name is required");
        }
        Objects.requireNonNull(dateColumn, "dateColumn");
        Objects.requireNonNull(descriptionColumn, "descriptionColumn");
        Objects.requireNonNull(dateFormat, "dateFormat");
        try {
            DateTimeFormatter.ofPattern(dateFormat);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid date format: " + dateFormat);
        }
        boolean single = amountColumn != null && !amountColumn.isBlank();
        boolean split = debitColumn != null && !debitColumn.isBlank() && creditColumn != null && !creditColumn.isBlank();
        if (single == split) {
            throw new IllegalArgumentException("Choose either one amount column or both debit and credit columns");
        }
    }

    public boolean usesSingleAmount() {
        return amountColumn != null && !amountColumn.isBlank();
    }

    /** Columns this mapping needs to find in the file. */
    public List<String> requiredColumns() {
        List<String> cols = new ArrayList<>(List.of(dateColumn, descriptionColumn));
        if (usesSingleAmount()) {
            cols.add(amountColumn);
        } else {
            cols.add(debitColumn);
            cols.add(creditColumn);
        }
        return cols;
    }

    /** True when every required column exists in the file's first row. */
    public boolean isValidFor(List<String> header) {
        for (String col : requiredColumns()) {
            if (indexOf(header, col) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Position of a column: by header name (case-insensitive) or by 1-based number for headerless files. */
    int indexOf(List<String> header, String column) {
        if (column == null || column.isBlank()) {
            return -1;
        }
        if (!hasHeader) {
            try {
                int i = Integer.parseInt(column.trim()) - 1;
                return i >= 0 && i < header.size() ? i : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(column.trim())) {
                return i;
            }
        }
        return -1;
    }

    DateTimeFormatter dateFormatter() {
        return DateTimeFormatter.ofPattern(dateFormat);
    }
}
