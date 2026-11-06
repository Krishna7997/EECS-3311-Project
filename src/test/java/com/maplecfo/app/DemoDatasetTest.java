package com.maplecfo.app;

import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Frequency;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.importer.ImportResult;
import com.maplecfo.importer.MappingMismatchException;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.planning.RrspRoomEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fake demo dataset in sample-data/demo (Jan 2025 - Nov 6 2026) used for testing and the feature videos:
 * every file imports cleanly and shows what each feature is meant to find.
 */
class DemoDatasetTest {

    private static final Path DEMO = Path.of("sample-data/demo");

    @TempDir
    Path dir;

    private MapleCfoFacade app;

    @BeforeEach
    void loadHistory() throws Exception {
        Clock nov6 = Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneId.of("America/Toronto"));
        app = AppContext.create(DatabaseManager.forFile(dir.resolve("demo.db")), nov6, AppConfig.of(java.util.Map.of()));
        app.addAccount(new Account("chequing", "TD Chequing", AccountType.CHEQUING, "TD", Money.of(3800)));
        app.addAccount(new Account("visa", "RBC Visa", AccountType.CREDIT_CARD, "RBC", Money.of(1850)));
        app.addAccount(new Account("savings", "EQ Bank Savings", AccountType.SAVINGS, "EQ Bank", Money.of(4800)));
        for (String[] f : List.of(new String[]{"td_chequing_2025-2026.csv", "chequing"},
                new String[]{"rbc_visa_2025-2026.csv", "visa"}, new String[]{"eq_savings_2025-2026.csv", "savings"})) {
            ImportResult r = app.importTransactionsAuto(DEMO.resolve(f[0]), f[1]);
            assertTrue(r.rejectedRows().isEmpty(), f[0] + " " + r.rejectedRows());
            assertTrue(r.imported() > 40, f[0]);
        }
    }

    private Optional<RecurringCharge> charge(String merchantStart) {
        return app.getRecurringCharges().stream()
                .filter(c -> c.merchant().toUpperCase().startsWith(merchantStart)).findFirst();
    }

    @Test
    void balancesAreSyncedFromTheFiles() {
        Money chequing = app.getAccounts().stream().filter(a -> a.getId().equals("chequing")).findFirst()
                .orElseThrow().getBalance();
        assertTrue(chequing.isGreaterThan(Money.of(1000)), chequing.toString());
        assertEquals(Money.of("8620.78"), app.getAccounts().stream().filter(a -> a.getId().equals("savings"))
                .findFirst().orElseThrow().getBalance());
    }

    @Test
    void recurringChargesShowPriceChangesBillsAndCancelledSubscriptions() {
        assertTrue(charge("NETFLIX").orElseThrow().priceIncreased(), "16.99 -> 18.99 in July");
        assertFalse(charge("NETFLIX").orElseThrow().variableAmount());
        assertTrue(charge("ROGERS").orElseThrow().priceIncreased(), "75 -> 82 in March");
        assertTrue(charge("SPOTIFY").orElseThrow().priceIncreased(), "11.99 -> 12.99 in September");
        assertTrue(charge("TORONTO HYDRO").orElseThrow().variableAmount());
        assertFalse(charge("TORONTO HYDRO").orElseThrow().priceIncreased());
        assertEquals(Frequency.WEEKLY, charge("PRESTO").orElseThrow().frequency());
        assertFalse(charge("GOODLIFE").orElseThrow().priceIncreased());
        assertTrue(charge("DISNEY").isEmpty(), "cancelled in April, so no longer listed");
    }

    @Test
    void octoberDiningGoesOverBudget() {
        app.setBudget(Category.DINING, Money.of(250), YearMonth.of(2026, 10));
        BudgetStatus dining = app.getBudgetStatus(YearMonth.of(2026, 10)).get(0);
        assertTrue(dining.isExceeded(), dining.spent().toString());
        String summary = app.generateMonthlyReport(YearMonth.of(2026, 10)).narrative();
        assertTrue(summary.contains("DINING is your biggest cost after rent; staying within its $250.00 budget"), summary);
        assertTrue(summary.contains("NETFLIX.COM 866-579-7172 now $18.99"), summary);
    }

    @Test
    void rrspEstimateUsesAFullYearOfPay() {
        RrspRoomEstimator.Estimate e = app.estimateRrspRoom(2026);
        assertEquals(RrspRoomEstimator.Status.ESTIMATED, e.status());
        assertEquals(12, e.monthsOfIncome());
        assertEquals(Money.of("54080.00"), e.incomeUsed());
        assertEquals(Money.of("9734.40"), e.estimate());
    }

    @Test
    void novemberFilesAndTheErrorFiles() throws Exception {
        ImportResult overlap = app.importTransactionsAuto(DEMO.resolve("errors/chequing_overlap_oct25-nov6.csv"), "chequing");
        assertEquals(12, overlap.imported(), "only the November rows are new");
        assertTrue(overlap.duplicatesSkipped() > 10);
        assertEquals(0, app.importTransactionsAuto(DEMO.resolve("inbox/chequing_nov2026.csv"), "chequing").imported());

        ImportResult bad = app.importTransactionsAuto(DEMO.resolve("errors/visa_bad_rows.csv"), "visa");
        assertEquals(3, bad.rejectedRows().size(), bad.rejectedRows().toString());
        assertEquals(3, bad.imported());

        assertTrue(app.detectImportFormat(DEMO.resolve("errors/unknown_bank_format.csv")).isEmpty());
        assertThrows(MappingMismatchException.class,
                () -> app.importTransactionsAuto(DEMO.resolve("errors/unknown_bank_format.csv"), "chequing"));
    }
}
