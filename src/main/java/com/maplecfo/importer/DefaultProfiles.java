package com.maplecfo.importer;

import java.util.List;

/**
 * Ready-made mapping profiles for common Canadian bank exports, so most users
 * never see the mapping dialog. Users can still create their own profiles.
 */
public final class DefaultProfiles {

    private DefaultProfiles() {
    }

    /** TD chequing/savings: no header; date, description, debit, credit, balance. */
    public static final ColumnMapping TD_BANK = new ColumnMapping("TD bank account", false, "1", "MM/dd/yyyy",
            "2", null, "3", "4", false, "5");

    /** RBC: header row, signed amount in "CAD$". */
    public static final ColumnMapping RBC = new ColumnMapping("RBC", true, "Transaction Date", "M/d/yyyy",
            "Description 1", "CAD$", null, null, false, null);

    /** Generic export with a header "Date,Description,Amount,Balance" and ISO dates. */
    public static final ColumnMapping GENERIC = new ColumnMapping("Generic (Date, Description, Amount)", true,
            "Date", "yyyy-MM-dd", "Description", "Amount", null, null, false, "Balance");

    public static List<ColumnMapping> all() {
        return List.of(TD_BANK, RBC, GENERIC);
    }
}
