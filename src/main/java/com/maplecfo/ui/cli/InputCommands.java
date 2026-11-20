package com.maplecfo.ui.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AgentResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * Other ways to give MapleCFO its input besides typing: a script file or standard input ({@code run}), questions
 * from a file or standard input ({@code ask --file}, {@code ask -}) and a small local HTTP API ({@code serve}).
 * All of them go through the same commands and the same facade, so the results are the same as when typed.
 */
final class InputCommands {

    private static final ObjectMapper JSON = new ObjectMapper();

    private InputCommands() {
    }

    // ------------------------------------------------------------------ run: commands from a file or stdin

    @Command(name = "run", description = {
            "Run MapleCFO commands from a file, one per line, e.g. run script.txt (use - for standard input).",
            "Lines starting with # are comments. All lines share one session, so AI proposals, confirm and undo "
                    + "work across lines. The exit code is 1 if any command failed."})
    static class RunCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(index = "0", description = "Script file, or - to read standard input") String source;
        @Option(names = "--stop-on-error", description = "Stop at the first command that fails") boolean stopOnError;
        @Option(names = {"-q", "--quiet"}, description = "Do not echo each command before its output") boolean quiet;

        @Override
        public Integer call() throws IOException {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            CommandLine cl = MapleCfoCli.newCommandLine(root);
            cl.setOut(out);
            cl.setErr(err);
            root.markInteractive();
            int run = 0;
            int failed = 0;
            try (BufferedReader in = reader(source)) {
                String line;
                while ((line = in.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) continue;
                    if (t.equalsIgnoreCase("exit") || t.equalsIgnoreCase("quit")) break;
                    List<String> args = MapleCfoCli.splitArgs(t);
                    if (args.get(0).equals("run") || args.get(0).equals("serve")) {
                        err.println("Error: '" + args.get(0) + "' cannot be used inside a script");
                        failed++;
                        continue;
                    }
                    if (!quiet) out.println("> " + t);
                    int code = cl.execute(args.toArray(String[]::new));
                    out.flush();
                    err.flush();
                    run++;
                    if (code != 0) {
                        failed++;
                        if (stopOnError) break;
                    }
                }
            }
            if (!quiet) out.println("Ran " + run + (run == 1 ? " command" : " commands") + ", " + failed + " failed.");
            return failed == 0 ? 0 : 1;
        }
    }

    // ------------------------------------------------------------------ serve: a local HTTP API

    @Command(name = "serve", description = {
            "Start a local HTTP API on this computer (127.0.0.1 only), e.g. serve --port 8080. Stop with Ctrl+C.",
            "GET /health; POST /ask with {\"question\": \"...\"}; POST /command with {\"command\": \"budget status\"}."})
    static class ServeCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Option(names = {"-p", "--port"}, defaultValue = "8080", description = "Port (default: 8080)") int port;

        @Override
        public Integer call() throws Exception {
            HttpServer server = start(root, port);
            PrintWriter out = spec.commandLine().getOut();
            out.println("MapleCFO API on http://127.0.0.1:" + server.getAddress().getPort()
                    + "  (GET /health, POST /ask, POST /command). Press Ctrl+C to stop.");
            out.flush();
            new CountDownLatch(1).await();
            return 0;
        }
    }

    /** Starts the API (package-private so tests can use port 0 and stop it). */
    static HttpServer start(MapleCfoCli root, int port) throws IOException {
        root.markInteractive();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        Object lock = new Object();
        server.createContext("/health", ex -> {
            ObjectNode o = JSON.createObjectNode();
            o.put("status", "ok");
            o.put("demoMode", root.facade().getLlm().isMock());
            o.put("model", root.facade().getLlm().modelName());
            send(ex, 200, o);
        });
        server.createContext("/ask", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, error("Use POST with {\"question\": \"...\"}")); return; }
            try {
                String q = field(ex.getRequestBody(), "question");
                AgentResponse r;
                synchronized (lock) {
                    r = root.facade().askCfo(q);
                }
                send(ex, 200, JSON.readTree(toJson(root, q, r)));
            } catch (IllegalArgumentException | IOException e) {
                send(ex, 400, error(e.getMessage()));
            } catch (RuntimeException e) {
                send(ex, 500, error(e.getMessage()));
            }
        });
        server.createContext("/command", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, error("Use POST with {\"command\": \"...\"}")); return; }
            try {
                String command = field(ex.getRequestBody(), "command");
                List<String> args = MapleCfoCli.splitArgs(command);
                if (args.isEmpty() || List.of("run", "serve", "gui").contains(args.get(0))) {
                    throw new IllegalArgumentException("This command cannot be run through the API: " + command);
                }
                StringWriter o = new StringWriter();
                StringWriter e = new StringWriter();
                int code;
                synchronized (lock) {
                    CommandLine cl = MapleCfoCli.newCommandLine(root);
                    cl.setOut(new PrintWriter(o, true));
                    cl.setErr(new PrintWriter(e, true));
                    code = cl.execute(args.toArray(String[]::new));
                }
                ObjectNode res = JSON.createObjectNode();
                res.put("command", command);
                res.put("exitCode", code);
                res.put("output", o.toString());
                res.put("error", e.toString());
                send(ex, 200, res);
            } catch (IllegalArgumentException | IOException e) {
                send(ex, 400, error(e.getMessage()));
            }
        });
        server.start();
        return server;
    }

    private static String field(InputStream body, String name) throws IOException {
        JsonNode n = JSON.readTree(body.readAllBytes());
        if (n == null || !n.hasNonNull(name) || n.get(name).asText().isBlank()) {
            throw new IllegalArgumentException("The JSON body needs a \"" + name + "\" field.");
        }
        return n.get(name).asText();
    }

    private static ObjectNode error(String msg) {
        ObjectNode o = JSON.createObjectNode();
        o.put("error", msg == null ? "unknown error" : msg);
        return o;
    }

    private static void send(HttpExchange ex, int status, JsonNode body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ------------------------------------------------------------------ shared helpers

    /** The questions for {@code ask}: the typed words, "-" (all of standard input), or a file / stdin, one per line. */
    static List<String> questions(List<String> words, String file) throws IOException {
        List<String> out = new ArrayList<>();
        if (file != null) {
            try (BufferedReader in = reader(file)) {
                String line;
                while ((line = in.readLine()) != null) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
                }
            }
        }
        if (words != null && !words.isEmpty()) {
            if (words.size() == 1 && words.get(0).equals("-")) {
                String all = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!all.isEmpty()) out.add(all);
            } else {
                out.add(String.join(" ", words));
            }
        }
        return out;
    }

    static BufferedReader reader(String source) throws IOException {
        if (source.equals("-")) {
            return new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        Path p = Path.of(source);
        if (!Files.isRegularFile(p)) {
            throw new IllegalArgumentException("File not found: " + source);
        }
        return Files.newBufferedReader(p, StandardCharsets.UTF_8);
    }

    /** One answer as a single line of JSON, for scripts and tests. */
    static String toJson(MapleCfoCli root, String question, AgentResponse r) throws IOException {
        ObjectNode o = JSON.createObjectNode();
        o.put("question", question);
        o.put("answer", r.answer());
        ArrayNode tools = o.putArray("toolsUsed");
        r.toolsUsed().forEach(tools::add);
        o.put("grounded", r.grounded());
        Map<String, String> pending = root.facade().getPendingActions();
        ArrayNode actions = o.putArray("proposedActions");
        for (String id : r.pendingActionIds()) {
            ObjectNode a = actions.addObject();
            a.put("id", id);
            a.put("description", pending.getOrDefault(id, ""));
        }
        o.put("demoMode", root.facade().getLlm().isMock());
        o.put("model", root.facade().getLlm().modelName());
        return JSON.writeValueAsString(o);
    }
}
