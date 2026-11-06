package com.maplecfo.services;

import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Transaction filters on the Transactions page and in "tx list". */
class TransactionFilterTest {

    @TempDir
    Path dir;
    private MapleCfoFacade app;

    @BeforeEach
    void setUp() throws Exception {
        Clock c = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Toronto"));
        app = AppContext.create(DatabaseManager.forFile(dir.resolve("f.db")), c);
        app.importTransactions(Path.of("sample-data/td_chequing_sample.csv"), "chequing", "TD bank account");
        app.importTransactions(Path.of("sample-data/rbc_visa_sample.csv"), "visa", "RBC");
    }

    @Test
    void noFilterReturnsEverythingNewestFirst() {
        List<Transaction> all = app.findTransactions(TransactionFilter.all());
        assertEquals(app.searchTransactions("").size(), all.size());
        for (int i = 1; i < all.size(); i++) assertFalse(all.get(i).getDate().isAfter(all.get(i - 1).getDate()));
    }

    @Test
    void filtersCombine() {
        TransactionFilter dining = TransactionFilter.all().category(Category.DINING).account("chequing")
                .between(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        List<Transaction> rows = app.findTransactions(dining);
        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(t -> t.getCategory() == Category.DINING && t.getAccountId().equals("chequing")
                && t.getDate().getMonthValue() == 10));
        assertEquals(Money.of("162.04"), TransactionFilter.totals(rows)[1], "October dining in the sample");
    }

    @Test
    void moneyInOutAndAmountRange() {
        List<Transaction> in = app.findTransactions(TransactionFilter.all().direction(TransactionFilter.Direction.IN)
                .account("chequing"));
        assertTrue(in.stream().noneMatch(Transaction::isExpense));
        assertTrue(in.stream().anyMatch(t -> t.getDescription().contains("PAYROLL")));

        List<Transaction> big = app.findTransactions(TransactionFilter.all().direction(TransactionFilter.Direction.OUT)
                .amounts(Money.of(1000), null));
        assertTrue(big.stream().allMatch(t -> t.getDescription().startsWith("RENT")), "only rent is over $1,000");

        List<Transaction> small = app.findTransactions(TransactionFilter.all().amounts(null, Money.of(10)));
        assertTrue(small.stream().allMatch(t -> !t.getAmount().abs().isGreaterThan(Money.of(10))));
    }

    @Test
    void textIgnoresCaseAndPunctuation() {
        assertFalse(app.findTransactions(TransactionFilter.all().text("netflix.com")).isEmpty());
        assertEquals(app.findTransactions(TransactionFilter.all().text("NETFLIX com")).size(),
                app.findTransactions(TransactionFilter.all().text("netflix")).size());
        assertTrue(app.findTransactions(TransactionFilter.all().account("savings")).isEmpty());
    }
}
