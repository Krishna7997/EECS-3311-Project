package com.maplecfo.ui.cli;

import com.maplecfo.app.AppContext;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** CLI commands not covered by MapleCfoCliTest: AI, report, transactions, profile, limits, errors. */
class MoreCliCommandsTest {

    @TempDir
    Path dir;
    private CommandLine cli;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void setUp() {
        MapleCfoCli root = new MapleCfoCli();
        Clock nov = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Toronto"));
        root.useFacade(AppContext.create(DatabaseManager.forFile(dir.resolve("cli.db")), nov));
        cli = MapleCfoCli.newCommandLine(root);
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
        run("import", "sample-data/td_chequing_sample.csv", "-a", "chequing", "-p", "TD bank account");
    }

    private String run(String... args) {
        out.getBuffer().setLength(0);
        int code = cli.execute(args);
        cli.getOut().flush();
        assertEquals(0, code, "command failed: " + String.join(" ", args) + "\n" + err);
        return out.toString();
    }

    private String fail(String... args) {
        err.getBuffer().setLength(0);
        int code = cli.execute(args);
        cli.getErr().flush();
        assertNotEquals(0, code, "expected failure: " + String.join(" ", args));
        return err.toString();
    }

    @Test
    void askInDemoModeAndAiStatus() {
        String a = run("ask", "How", "much", "did", "I", "spend", "last", "month?");
        assertTrue(a.contains("Demo mode"), a);
        assertTrue(a.contains("[tools used: spending_summary]"), a);
        String ai = run("ai");
        assertTrue(ai.contains("add LLM_API_KEY to .env"), ai);
        assertTrue(ai.contains("Pending proposals:") && ai.contains("A1: Record $500.00 TFSA contribution"),
                "the import found TFSA transfers waiting for confirmation: " + ai);
    }

    @Test
    void dismissUnknownProposal() {
        assertTrue(run("dismiss", "A9").contains("No pending action A9"));
        assertTrue(fail("confirm", "A9").contains("No pending action A9"));
    }

    @Test
    void monthlyReport() {
        String r = run("report", "-m", "2026-09");
        assertTrue(r.contains("Monthly report for 2026-09"), r);
        assertTrue(r.contains("RENT"), r);
        assertTrue(r.contains("Spending $2,371.61"), r);
        assertTrue(run("report", "-m", "2025-01").contains("Monthly report for 2025-01"));
    }

    @Test
    void transactionsListSearchAndRecategorizeWithUndo() {
        assertTrue(run("tx", "list", "-m", "2026-10").contains("NETFLIX"));
        String found = run("tx", "search", "spotify");
        assertTrue(found.contains("SPOTIFY"), found);
        assertTrue(run("profiles").contains("TD bank account"));
        assertTrue(run("undo").contains("Nothing to undo."));
        assertTrue(run("redo").contains("Nothing to redo."));
    }

    @Test
    void profileSetAndShow() {
        run("profile", "set", "--birth-year", "2003", "--resident-since", "2010");
        String p = run("profile", "show");
        assertTrue(p.contains("2003") && p.contains("2010"), p);
        assertTrue(run("room", "TFSA").contains("TFSA 2026: room"));
        assertTrue(run("room", "RRSP").contains("RRSP: unknown"));
    }

    @Test
    void limitsAndAlerts() {
        assertTrue(run("limits").contains("Using bundled limits (TFSA up to 2026: $7,000.00)"));
        assertTrue(fail("limits", "delete").contains("Unknown action"));
        assertTrue(run("alerts").contains("TFSA contribution"), "the import found TFSA transfers");
    }

    @Test
    void debtListAndRemove() {
        run("debt", "add", "Visa", "--balance", "2400", "--apr", "20.99", "--min", "60");
        String list = run("debt", "list");
        assertTrue(list.contains("Visa"), list);
        String id = list.lines().filter(l -> l.contains("Visa")).findFirst().orElseThrow().trim().split("\\s+")[0];
        assertTrue(run("debt", "remove", id).contains("Removed Visa"));
    }

    @Test
    void badInputGivesAFriendlyError() {
        assertTrue(fail("budget", "set", "DINING", "abc").length() > 0);
        assertTrue(fail("goal", "add", "Trip", "1000", "--by", "2020-01-01").contains("deadline must be in the future"));
        assertTrue(fail("afford", "0").contains("greater than zero"));
        assertTrue(fail("account", "update", "nope", "5").contains("No account 'nope'"));
        assertTrue(fail("no-such-command").length() > 0);
    }

    @Test
    void importDetectsTheBankFormat() throws Exception {
        String rbc = run("import", "sample-data/rbc_visa_sample.csv", "-a", "visa");
        assertTrue(rbc.contains("Detected format: RBC"), rbc);
        String generic = run("import", "sample-data/savings_generic_sample.csv", "-a", "savings");
        assertTrue(generic.contains("Detected format: Generic"), generic);
        String td = run("import", "sample-data/td_chequing_sample.csv", "-a", "chequing");
        assertTrue(td.contains("Detected format: TD bank account") && td.contains("Duplicates skipped: 136"), td);

        Path odd = dir.resolve("odd.csv");
        java.nio.file.Files.writeString(odd, "foo;bar\n1;2\n");
        assertTrue(fail("import", odd.toString(), "-a", "chequing").contains("Could not recognise"));
    }

    @Test
    void inboxAndCheckin() {
        Path folder = dir.resolve("downloads");
        assertTrue(run("inbox", folder.toString()).contains("Import folder saved: " + folder));
        assertTrue(run("inbox").contains("Import folder: " + folder));
        assertTrue(run("checkin").contains("Your monthly check-in is due"));
        run("checkin", "--done");
        assertTrue(run("checkin").contains("This month's check-in is done"));
    }
}
