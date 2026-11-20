package com.maplecfo.ui.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maplecfo.app.AppContext;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** Input from a script file, standard input and the local HTTP API (no API key: the offline mock model answers). */
class InputCommandsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;
    private MapleCfoCli root;
    private CommandLine cli;
    private StringWriter out;
    private StringWriter err;
    private final InputStream realIn = System.in;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        root = new MapleCfoCli();
        Clock oct2026 = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Toronto"));
        root.useFacade(AppContext.create(DatabaseManager.forFile(dir.resolve("cli.db")), oct2026));
        cli = MapleCfoCli.newCommandLine(root);
        out = new StringWriter();
        err = new StringWriter();
        cli.setOut(new PrintWriter(out, true));
        cli.setErr(new PrintWriter(err, true));
    }

    @AfterEach
    void tearDown() {
        System.setIn(realIn);
        if (server != null) server.stop(0);
    }

    @Test
    void runsAScriptFileInOneSession() throws Exception {
        Path script = dir.resolve("script.txt");
        Files.writeString(script, """
                # import, set a budget, then undo it in the same session
                import sample-data/td_chequing_sample.csv -a chequing -p "TD bank account"
                budget set dining 150

                undo
                budget status
                """);
        int code = cli.execute("run", script.toString());
        String o = out.toString();
        assertEquals(0, code, err.toString());
        assertTrue(o.contains("> budget set dining 150"), o);
        assertTrue(o.contains("Imported 136 transactions"), o);
        assertTrue(o.contains("Undone: Set DINING budget"), "undo works across lines of one script: " + o);
        assertTrue(o.contains("Ran 4 commands, 0 failed."), o);
    }

    @Test
    void scriptReportsFailuresWithExitCode() throws Exception {
        Path script = dir.resolve("bad.txt");
        Files.writeString(script, "budget set dining -5\nnetworth\n");
        assertEquals(1, cli.execute("run", script.toString()));
        assertTrue(out.toString().contains("Ran 2 commands, 1 failed."), out.toString());
        assertTrue(err.toString().contains("Budget limit must be greater than zero"), err.toString());

        out.getBuffer().setLength(0);
        assertEquals(1, cli.execute("run", "--stop-on-error", script.toString()));
        assertTrue(out.toString().contains("Ran 1 command, 1 failed."), out.toString());
    }

    @Test
    void runsCommandsFromStandardInput() {
        System.setIn(new ByteArrayInputStream("networth\nexit\nnetworth\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, cli.execute("run", "-"));
        assertTrue(out.toString().contains("Ran 1 command, 0 failed."), "stops at exit: " + out);
    }

    @Test
    void missingScriptIsAClearError() {
        assertEquals(1, cli.execute("run", dir.resolve("nope.txt").toString()));
        assertTrue(err.toString().contains("File not found"), err.toString());
    }

    @Test
    void asksEveryQuestionInAFileAsJsonLines() throws Exception {
        cli.execute("import", "sample-data/td_chequing_sample.csv", "-a", "chequing", "-p", "TD bank account");
        out.getBuffer().setLength(0);
        Path q = dir.resolve("questions.txt");
        Files.writeString(q, "How much TFSA room do I have?\n# a comment\n\nWhat is my net worth?\n");
        assertEquals(0, cli.execute("ask", "--file", q.toString(), "--json"), err.toString());
        String[] lines = out.toString().strip().split("\n");
        assertEquals(2, lines.length, out.toString());
        JsonNode first = JSON.readTree(lines[0]);
        assertEquals("How much TFSA room do I have?", first.get("question").asText());
        assertFalse(first.get("answer").asText().isBlank());
        assertTrue(first.get("toolsUsed").isArray());
        assertTrue(first.get("demoMode").asBoolean(), "no key in tests, so the mock model answers");
        assertEquals("What is my net worth?", JSON.readTree(lines[1]).get("question").asText());
    }

    @Test
    void readsTheQuestionFromStandardInput() {
        System.setIn(new ByteArrayInputStream("How much TFSA room do I have?\n".getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, cli.execute("ask", "-"), err.toString());
        assertTrue(out.toString().contains("TFSA room"), out.toString());
    }

    @Test
    void askWithoutAQuestionExplainsHow() {
        assertEquals(1, cli.execute("ask"));
        assertTrue(err.toString().contains("--file"), err.toString());
    }

    @Test
    void httpApiAnswersHealthAskAndCommand() throws Exception {
        server = InputCommands.start(root, 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

        HttpResponse<String> health = http.send(HttpRequest.newBuilder(URI.create(base + "/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertEquals("ok", JSON.readTree(health.body()).get("status").asText());

        HttpResponse<String> ask = http.send(post(base + "/ask", "{\"question\":\"How much TFSA room do I have?\"}"),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ask.statusCode(), ask.body());
        assertTrue(JSON.readTree(ask.body()).get("answer").asText().contains("TFSA"), ask.body());

        HttpResponse<String> cmd = http.send(post(base + "/command", "{\"command\":\"budget set dining 150\"}"),
                HttpResponse.BodyHandlers.ofString());
        JsonNode c = JSON.readTree(cmd.body());
        assertEquals(0, c.get("exitCode").asInt(), cmd.body());
        assertTrue(c.get("output").asText().contains("Budget set: DINING $150.00"), cmd.body());

        HttpResponse<String> home = http.send(HttpRequest.newBuilder(URI.create(base + "/")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, home.statusCode());
        assertTrue(home.body().contains("POST /ask"), home.body());
        HttpResponse<String> unknown = http.send(HttpRequest.newBuilder(URI.create(base + "/nope")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, unknown.statusCode());
        assertTrue(unknown.body().contains("endpoints"), "a wrong path explains the API: " + unknown.body());
        HttpResponse<String> getAsk = http.send(HttpRequest.newBuilder(URI.create(
                base + "/ask?question=How+much+TFSA+room+do+I+have%3F")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getAsk.statusCode(), getAsk.body());
        assertEquals("How much TFSA room do I have?", JSON.readTree(getAsk.body()).get("question").asText());

        HttpResponse<String> bad = http.send(post(base + "/ask", "{}"), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, bad.statusCode());
        HttpResponse<String> notAllowed = http.send(post(base + "/command", "{\"command\":\"serve\"}"),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, notAllowed.statusCode());
    }

    private static HttpRequest post(String url, String body) {
        return HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }
}
