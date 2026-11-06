package com.maplecfo.automation;

import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.events.EventType;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.planning.RrspRoomEstimator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** F13 automatic import, balance sync and reminders; F14 contribution detection and RRSP estimate. */
class AutomationTest {

    @TempDir
    Path dir;

    private MapleCfoFacade app(String isoInstant) {
        Clock c = Clock.fixed(Instant.parse(isoInstant), ZoneId.of("America/Toronto"));
        return AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")), c);
    }

    private MapleCfoFacade oct21() {
        return app("2026-10-21T15:00:00Z");
    }

    private static final Path TD = Path.of("sample-data/td_chequing_sample.csv");
    private static final Path RBC = Path.of("sample-data/rbc_visa_sample.csv");

    // ------------------------------------------------------------------ F13 import folder

    @Test
    void scanImportsEachFileWithTheRightFormatAndAccount() throws Exception {
        MapleCfoFacade app = oct21();
        app.addAccount(new Account("visa", "RBC Visa", AccountType.CREDIT_CARD, "RBC", Money.of(0)));
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        Files.copy(TD, inbox.resolve("td_export_oct.csv"));
        Files.copy(RBC, inbox.resolve("visa_statement.csv"));
        Files.writeString(inbox.resolve("mystery.csv"), "foo;bar\n1;2\n");

        ImportFolderWatcher w = app.newImportWatcher(inbox, "chequing");
        List<ImportFolderWatcher.Outcome> out = w.scanOnce();
        assertEquals(3, out.size());
        String all = out.stream().map(ImportFolderWatcher.Outcome::message).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(all.contains("td_export_oct.csv → chequing (TD bank account): 136 imported"), all);
        assertTrue(all.contains("visa_statement.csv → visa (RBC)"), all);
        assertTrue(all.contains("mystery.csv: no saved bank format matches"), all);
        assertEquals(2, out.stream().filter(ImportFolderWatcher.Outcome::imported).count());
        assertTrue(app.getAlerts().stream().anyMatch(a -> a.source() == EventType.AUTO_IMPORTED));

        assertTrue(w.scanOnce().isEmpty(), "files already processed are not imported again");
    }

    @Test
    void watcherPicksUpANewFile() throws Exception {
        MapleCfoFacade app = oct21();
        Path inbox = Files.createDirectories(dir.resolve("inbox"));
        try (ImportFolderWatcher w = app.newImportWatcher(inbox, "chequing")) {
            w.start();
            Files.copy(TD, inbox.resolve("new_download.csv"));
            long until = System.currentTimeMillis() + 30_000; // macOS polls folders every ~10 s
            while (app.getTransactions(java.time.YearMonth.of(2026, 10)).isEmpty() && System.currentTimeMillis() < until) {
                Thread.sleep(200);
            }
        }
        assertFalse(app.getTransactions(java.time.YearMonth.of(2026, 10)).isEmpty(), "the dropped file was imported");
    }

    @Test
    void importUpdatesTheAccountBalance() throws Exception {
        MapleCfoFacade app = oct21();
        app.addAccount(new Account("chequing", "TD Chequing", AccountType.CHEQUING, "TD", Money.of(100)));
        app.importTransactions(TD, "chequing", "TD bank account");
        assertEquals(Money.of("7999.73"), app.getAccounts().get(0).getBalance(), "last running balance in the file");
        assertEquals(Money.of("7999.73"), app.getNetWorth().total());
    }

    // ------------------------------------------------------------------ F13 reminders

    @Test
    void remindersAreRaisedOnce() throws Exception {
        MapleCfoFacade app = oct21();
        assertTrue(app.checkReminders().isEmpty(), "nothing imported yet: nothing to remind");
        app.importTransactions(TD, "chequing", "TD bank account");
        List<String> due = app.checkReminders();
        assertEquals(1, due.size(), due.toString());
        assertTrue(due.get(0).contains("September summary is ready"), due.get(0));
        assertTrue(app.checkReminders().isEmpty(), "not repeated");

        MapleCfoFacade later = app("2026-12-15T15:00:00Z"); // same database, two months later
        List<String> stale = later.checkReminders();
        assertTrue(stale.stream().anyMatch(s -> s.startsWith("No new transactions in 56 days")), stale.toString());
        assertTrue(later.getAlerts().stream().anyMatch(a -> a.source() == EventType.REMINDER_DUE));
    }

    // ------------------------------------------------------------------ F14 contributions

    @Test
    void detectsOnlyClearContributions() {
        assertEquals(Optional.of(new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 6, 25), Money.of(500),
                ContributionKind.CONTRIBUTION)), ContributionDetector.detect(tx("TFSA CONTRIBUTION - WEALTHSIMPLE", "-500")));
        assertEquals(ContributionKind.WITHDRAWAL,
                ContributionDetector.detect(tx("RRSP WITHDRAWAL", "1000")).orElseThrow().kind());
        assertTrue(ContributionDetector.detect(tx("TFSA TO RRSP TRANSFER", "-500")).isEmpty(), "ambiguous");
        assertTrue(ContributionDetector.detect(tx("FHSA INTEREST", "3.20")).isEmpty(), "money in, not a withdrawal");
        assertTrue(ContributionDetector.detect(tx("TFSAVINGS BANK", "-20")).isEmpty(), "word must match exactly");
        assertTrue(ContributionDetector.detect(tx("PAYROLL DEP ACME", "1890")).isEmpty());
    }

    private static Transaction tx(String desc, String amount) {
        Transaction t = new Transaction("t-" + desc.hashCode(), LocalDate.of(2026, 6, 25), desc, desc,
                Money.of(amount), "chequing");
        t.setCategory(Category.TRANSFER, CategorySource.RULE);
        return t;
    }

    @Test
    void importProposesContributionsThatNeedConfirmation() throws Exception {
        MapleCfoFacade app = oct21();
        UserProfile p = new UserProfile(2003, "ON", 2003, null, null, null);
        app.saveProfile(p);
        Money before = app.getContributionRoom(AccountType.TFSA, 2026).used();

        app.importTransactions(TD, "chequing", "TD bank account");
        Map<String, String> pending = app.getPendingActions();
        assertEquals(4, pending.size(), pending.toString());
        assertTrue(pending.values().stream().allMatch(s -> s.startsWith("Record $500.00 TFSA contribution")));
        assertEquals(before, app.getContributionRoom(AccountType.TFSA, 2026).used(), "nothing saved before confirming");
        assertEquals(4, app.getAlerts().stream().filter(a -> a.source() == EventType.CONTRIBUTION_DETECTED).count());

        List<String> ids = List.copyOf(pending.keySet());
        app.confirmProposedAction(ids.get(0));
        app.confirmProposedAction(ids.get(1));
        app.dismissProposedAction(ids.get(2));
        assertEquals(before.plus(Money.of(1000)), app.getContributionRoom(AccountType.TFSA, 2026).used());
        assertTrue(app.undoLastEdit());
        assertEquals(before.plus(Money.of(500)), app.getContributionRoom(AccountType.TFSA, 2026).used());

        app.importTransactions(TD, "chequing", "TD bank account");
        assertEquals(1, app.getPendingActions().size(), "re-importing the file proposes nothing new");
    }

    // ------------------------------------------------------------------ F14 RRSP estimate

    @Test
    void rrspEstimateFromPayDeposits() throws Exception {
        MapleCfoFacade app = oct21();
        app.importTransactions(TD, "chequing", "TD bank account");
        assertEquals(RrspRoomEstimator.Status.UNKNOWN, app.estimateRrspRoom(2026).status(), "no 2025 pay imported");

        RrspRoomEstimator.Estimate e = app.estimateRrspRoom(2027);
        assertEquals(RrspRoomEstimator.Status.ESTIMATED, e.status());
        assertEquals(5, e.monthsOfIncome());
        assertEquals(Money.of(45360), e.incomeUsed(), "10 x $1,890 over 5 months, scaled to 12");
        assertEquals(Money.of("8164.80"), e.estimate(), "18% of $45,360 is under the yearly maximum");
    }
}
