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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Runs CLI commands end to end against a temporary database (the same path the real app uses). */
class MapleCfoCliTest {

    @TempDir
    Path dir;
    private CommandLine cli;
    private StringWriter out;
    private StringWriter err;

    @BeforeEach
    void setUp() {
        MapleCfoCli root = new MapleCfoCli();
        Clock oct2026 = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Toronto"));
        root.useFacade(AppContext.create(DatabaseManager.forFile(dir.resolve("cli.db")), oct2026));
        cli = MapleCfoCli.newCommandLine(root);
        out = new StringWriter();
        err = new StringWriter();
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
    }

    private String run(String... args) {
        out.getBuffer().setLength(0);
        int code = cli.execute(args);
        cli.getOut().flush();
        assertEquals(0, code, "command failed: " + String.join(" ", args) + "\n" + err);
        return out.toString();
    }

    @Test
    void importThenBudgetThenUndo() {
        String imported = run("import", "sample-data/td_chequing_sample.csv", "-a", "chequing", "-p", "TD bank account");
        assertTrue(imported.contains("Imported 136 transactions"), imported);

        String set = run("budget", "set", "dining", "150");
        assertTrue(set.contains("Budget set: DINING $150.00 for 2026-10"), set);
        assertTrue(set.contains("DINING budget exceeded"), "the sample data overspends on dining: " + set);

        assertTrue(run("budget", "status").contains("DINING"));
        assertTrue(run("undo").contains("Undone: Set DINING budget"));
        assertTrue(run("budget", "status").contains("No budgets for 2026-10"));
    }

    @Test
    void recurringFindsTheSampleSubscriptions() {
        run("import", "sample-data/td_chequing_sample.csv", "-a", "chequing", "-p", "TD bank account");
        String r = run("recurring");
        assertTrue(r.contains("NETFLIX"), r);
        assertTrue(r.contains("SPOTIFY"), r);
    }

    @Test
    void fireAndDebtPlan() {
        String fire = run("fire", "--save", "15000", "--spend", "40000", "--invested", "10000");
        assertTrue(fire.contains("FIRE target: $1,000,000.00"), fire);

        run("debt", "add", "Visa", "--balance", "2400", "--apr", "20.99", "--min", "60");
        String plan = run("debt", "plan", "--compare", "--extra", "200");
        assertTrue(plan.contains("Avalanche") && plan.contains("Snowball"), plan);
    }

    @Test
    void roomNeedsAProfileFirst() {
        assertTrue(run("room", "TFSA").contains("unknown"));
        run("profile", "set", "--birth-year", "2003", "--resident-since", "2010");
        assertTrue(run("room", "TFSA").contains("available $39,500.00"));
        assertTrue(run("room", "check", "TFSA", "45000").startsWith("OVER"));
    }

    @Test
    void badInputGivesAClearMessage() {
        assertEquals(2, cli.execute("budget", "set", "DINING", "abc"));
        assertTrue(err.toString().contains("'abc' is not an amount"));
        assertEquals(1, cli.execute("import", "missing.csv", "-a", "x", "-p", "RBC"));
        assertTrue(err.toString().startsWith("Error:") || err.toString().contains("Error:"));
    }

    @Test
    void shellSplitsQuotedArguments() {
        assertEquals(List.of("debt", "add", "RBC Visa", "--apr", "20"),
                MapleCfoCli.splitArgs("debt add \"RBC Visa\"  --apr 20"));
    }

    @Test
    void accountsNetWorthAndGoals() {
        assertTrue(run("networth").contains("Add your first account"));
        run("account", "add", "TD Chequing", "CHEQUING", "2500", "--bank", "TD");
        run("account", "add", "RBC Visa", "CREDIT_CARD", "800");
        assertTrue(run("account", "list").contains("td-chequing"));
        String nw = run("networth");
        assertTrue(nw.contains("Net worth: $1,700.00"), nw);
        assertTrue(run("account", "update", "rbc-visa", "200").contains("Net worth is now $2,300.00"));

        String added = run("goal", "add", "Emergency fund", "1000", "--by", "2027-10-21", "--saved", "900");
        assertTrue(added.contains("emergency-fund"), added);
        assertTrue(run("goal", "list").contains("Emergency fund"));
        String dep = run("goal", "deposit", "emergency-fund", "100");
        assertTrue(dep.contains("Goal reached: Emergency fund"), dep);
        assertTrue(run("goal", "forecast", "Emergency fund").contains("is complete"));
    }

    @Test
    void affordCommand() {
        run("import", "sample-data/td_chequing_sample.csv", "-a", "chequing", "-p", "TD bank account");
        run("account", "add", "TD Chequing", "CHEQUING", "2500");
        String r = run("afford", "300", "--by", "2027-01-31");
        assertTrue(r.contains("Verdict: YES"), r);
        assertTrue(run("afford", "900000").contains("Verdict: NO"));
    }

    @Test
    void watchOnceRemindersAndRrspEstimate() throws Exception {
        Path inbox = java.nio.file.Files.createDirectories(dir.resolve("inbox"));
        java.nio.file.Files.copy(Path.of("sample-data/td_chequing_sample.csv"), inbox.resolve("oct.csv"));
        String w = run("watch", "--once", "--folder", inbox.toString());
        assertTrue(w.contains("oct.csv → chequing (TD bank account): 136 imported"), w);
        assertTrue(w.contains("Detected a $500.00 TFSA contribution"), w);
        assertTrue(w.contains("run maplecfo with no arguments"), w);
        assertTrue(run("reminders").contains("September summary is ready"));
        assertTrue(run("rrsp-estimate", "-y", "2027").contains("Estimated RRSP room for 2027: $8,164.80"));
    }
}
