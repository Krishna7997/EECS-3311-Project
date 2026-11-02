package com.maplecfo.importer;

import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvTransactionAdapterTest {

    @TempDir
    Path dir;

    private Path write(String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    @Test
    void csvReaderHandlesQuotesCommasAndBom() {
        List<List<String>> rows = CsvReader.parse("﻿a,\"b, with comma\",\"say \"\"hi\"\"\"\r\n\r\n1,2,3");
        assertEquals(List.of("﻿a", "b, with comma", "say \"hi\""), rows.get(0));
        assertEquals(2, rows.size(), "blank line skipped");
    }

    @Test
    void readsTdHeaderlessDebitCreditLayout() throws IOException {
        Path f = write("td.csv", "10/01/2026,LOBLAWS #1032,54.20,,945.80\n10/02/2026,PAYROLL DEP,,1890.00,2835.80\n");
        CsvTransactionAdapter a = new CsvTransactionAdapter(DefaultProfiles.TD_BANK);
        List<Transaction> txs = a.importFile(f, "chq");
        assertEquals(2, txs.size());
        assertEquals(Money.of("-54.20"), txs.get(0).getAmount());
        assertEquals(Money.of("1890.00"), txs.get(1).getAmount());
        assertEquals(LocalDate.of(2026, 10, 2), txs.get(1).getDate());
        assertEquals(Money.of("2835.80"), a.getLastBalance().orElseThrow());
    }

    @Test
    void readsRbcSignedAmountsAndReportsBadRows() throws IOException {
        Path f = write("rbc.csv", "\"Account Type\",\"Transaction Date\",\"Description 1\",\"CAD$\"\n"
                + "Visa,7/4/2026,\"PETRO-CANADA 4412\",-72.48\n"
                + "Visa,13/45/2026,\"BROKEN\",-5.00\n"
                + "Visa,7/5/2026,\"\",-3.00\n"
                + "Visa,7/6/2026,\"AMAZON.CA\",abc\n");
        CsvTransactionAdapter a = new CsvTransactionAdapter(DefaultProfiles.RBC);
        List<Transaction> txs = a.importFile(f, "visa");
        assertEquals(1, txs.size());
        assertEquals(3, a.getRejectedRows().size());
        assertTrue(a.getRejectedRows().get(0).startsWith("Row 3: invalid date"));
        assertTrue(a.getRejectedRows().get(1).contains("missing description"));
        assertTrue(a.getRejectedRows().get(2).contains("invalid amount"));
    }

    @Test
    void negateAmountsForCardsThatExportPurchasesAsPositive() throws IOException {
        ColumnMapping card = new ColumnMapping("Card", true, "Date", "yyyy-MM-dd", "Item", "Amount",
                null, null, true, null);
        Path f = write("card.csv", "Date,Item,Amount\n2026-10-01,Coffee,4.50\n");
        assertEquals(Money.of("-4.50"), new CsvTransactionAdapter(card).importFile(f, "c").get(0).getAmount());
    }

    @Test
    void profileThatDoesNotFitTheHeaderIsRejected() throws IOException {
        Path f = write("other.csv", "When,What,How much\n2026-10-01,Coffee,4.50\n");
        assertThrows(MappingMismatchException.class,
                () -> new CsvTransactionAdapter(DefaultProfiles.GENERIC).importFile(f, "x"));
    }

    @Test
    void emptyFileIsAnError() throws IOException {
        Path f = write("empty.csv", "\n\n");
        assertThrows(IOException.class, () -> new CsvTransactionAdapter(DefaultProfiles.GENERIC).importFile(f, "x"));
    }

    @Test
    void parsesCommonAmountFormats() {
        assertEquals("1234.50", CsvTransactionAdapter.parseAmount("$1,234.50", "a").toPlainString());
        assertEquals("-4.50", CsvTransactionAdapter.parseAmount("(4.50)", "a").toPlainString());
    }

    @Test
    void mappingNeedsExactlyOneAmountStyle() {
        assertThrows(IllegalArgumentException.class, () ->
                new ColumnMapping("Bad", true, "Date", "yyyy-MM-dd", "Desc", "Amount", "Debit", "Credit", false, null));
        assertThrows(IllegalArgumentException.class, () ->
                new ColumnMapping("Bad", true, "Date", "yyyy-MM-dd", "Desc", null, null, null, false, null));
    }
}
