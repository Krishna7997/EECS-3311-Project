package com.maplecfo.ui.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maplecfo.app.AppContext;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** Standalone task mode: java -jar maplecfo.jar -input task.txt (offline model, temporary database). */
class TaskModeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;
    private CommandLine cli;
    private StringWriter out;
    private StringWriter err;

    @BeforeEach
    void setUp() {
        MapleCfoCli root = new MapleCfoCli();
        Clock oct2026 = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Toronto"));
        root.useFacade(AppContext.create(DatabaseManager.forFile(dir.resolve("task.db")), oct2026));
        cli = MapleCfoCli.newCommandLine(root);
        out = new StringWriter();
        err = new StringWriter();
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
        assertEquals(0, cli.execute("import", "sample-data/td_chequing_sample.csv", "-a", "chequing", "-p", "TD bank account"));
        out.getBuffer().setLength(0);
    }

    private Path task(String text) throws Exception {
        Path f = dir.resolve("task.txt");
        Files.writeString(f, text);
        return f;
    }

    @Test
    void runsTheTaskInTheFileAndPrintsTheResult() throws Exception {
        Path f = task("# comment lines are ignored\nHow much did I spend on dining in October 2026?\n");
        assertEquals(0, cli.execute("-input", f.toString()), err.toString());
        String o = out.toString();
        assertTrue(o.startsWith("Task: How much did I spend on dining in October 2026?"), o);
        assertTrue(o.contains("Tools used: spending_summary"), o);
        assertTrue(o.contains("Every amount checked against the tool results: yes"), o);
    }

    @Test
    void jsonOutputAndOutputFile() throws Exception {
        Path f = task("Can I afford a $1,500 laptop by March 31, 2027?");
        Path saved = dir.resolve("result.json");
        assertEquals(0, cli.execute("--input", f.toString(), "-json", "-output", saved.toString()), err.toString());
        JsonNode r = JSON.readTree(out.toString());
        assertEquals("Can I afford a $1,500 laptop by March 31, 2027?", r.get("task").asText());
        assertEquals("affordability", r.get("toolsUsed").get(0).asText());
        assertTrue(r.get("grounded").asBoolean());
        assertEquals(r, JSON.readTree(Files.readString(saved)), "the saved file holds the same result");
    }

    @Test
    void proposedChangesAreAppliedOnlyWithConfirm() throws Exception {
        Path f = task("Set my dining budget to $300.");
        assertEquals(0, cli.execute("-input", f.toString()), err.toString());
        assertTrue(out.toString().contains("Proposed changes (not applied; add -confirm to apply them)"), out.toString());
        out.getBuffer().setLength(0);
        cli.execute("budget", "status");
        assertFalse(out.toString().contains("$300.00"), "nothing changed without -confirm: " + out);

        out.getBuffer().setLength(0);
        assertEquals(0, cli.execute("-input", f.toString(), "-confirm"), err.toString());
        assertTrue(out.toString().contains("Changes applied (-confirm)"), out.toString());
        out.getBuffer().setLength(0);
        cli.execute("budget", "status");
        assertTrue(out.toString().contains("$300.00"), "the budget was set with -confirm: " + out);
    }

    @Test
    void readsTheTaskFromStandardInput() {
        java.io.InputStream real = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream("What is my net worth?".getBytes()));
            assertEquals(0, cli.execute("-input", "-"), err.toString());
            assertTrue(out.toString().startsWith("Task: What is my net worth?"), out.toString());
        } finally {
            System.setIn(real);
        }
    }

    @Test
    void clearErrorsForMissingEmptyOrInvalidInput() throws Exception {
        assertEquals(1, cli.execute("-input", dir.resolve("missing.txt").toString()));
        assertTrue(err.toString().contains("File not found"), err.toString());
        assertEquals(1, cli.execute("-input", task("# only a comment\n").toString()));
        assertTrue(err.toString().contains("The task file is empty"), err.toString());
        assertEquals(1, cli.execute("-input", task("What is my net worth?").toString(), "-max-steps", "0"));
        assertTrue(err.toString().contains("--max-steps must be between 1 and 20"), err.toString());
    }

    @Test
    void llmApiAndOfflineCannotBeCombined() throws Exception {
        MapleCfoCli fresh = new MapleCfoCli();
        CommandLine c = MapleCfoCli.newCommandLine(fresh);
        StringWriter e = new StringWriter();
        c.setErr(new PrintWriter(e, true));
        c.setOut(new PrintWriter(new StringWriter(), true));
        assertEquals(1, c.execute("-LLMAPI", "-offline", "--db", dir.resolve("x.db").toString(), "-input",
                task("What is my net worth?").toString()));
        assertTrue(e.toString().contains("either -LLMAPI or -offline"), e.toString());
    }
}
