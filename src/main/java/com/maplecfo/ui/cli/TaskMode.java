package com.maplecfo.ui.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AgentResponse;
import com.maplecfo.app.MapleCfoFacade;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Standalone task mode: {@code java -jar maplecfo.jar -LLMAPI -input task.txt}. The whole file is one task for the
 * agent, which may use up to {@code -max-steps} tool calls. The result (answer, tools used, whether every amount was
 * checked against the tool results, proposed and applied changes) is printed as text or JSON and can be saved with
 * {@code -output}. Proposed changes are applied only with {@code -confirm}.
 */
final class TaskMode {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TaskMode() {
    }

    static int run(MapleCfoCli root, PrintWriter out, PrintWriter err) {
        try {
            String task = read(root.taskFile);
            MapleCfoFacade app = root.facade();
            root.markInteractive();
            AgentResponse r = app.runAgentTask(task, root.maxSteps);
            Map<String, String> proposed = new LinkedHashMap<>();
            Map<String, String> pending = app.getPendingActions();
            for (String id : r.pendingActionIds()) proposed.put(id, pending.getOrDefault(id, ""));
            Map<String, String> applied = new LinkedHashMap<>();
            if (root.confirmChanges) {
                for (String id : proposed.keySet()) applied.put(id, app.confirmProposedAction(id));
            }
            String text = root.taskJson ? json(task, r, proposed, applied, app) : text(task, r, proposed, applied, app);
            out.println(text);
            out.flush();
            if (root.outputFile != null) {
                Files.writeString(root.outputFile, text + System.lineSeparator(), StandardCharsets.UTF_8);
                if (!root.taskJson) out.println("(Saved to " + root.outputFile + ")");
            }
            return 0;
        } catch (IOException | RuntimeException e) {
            err.println("Error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            err.flush();
            return 1;
        }
    }

    private static String read(String source) throws IOException {
        try (BufferedReader in = InputCommands.reader(source)) {
            String task = in.lines().filter(l -> !l.trim().startsWith("#")).collect(Collectors.joining("\n")).trim();
            if (task.isEmpty()) {
                throw new IllegalArgumentException("The task file is empty: write the task in plain English, e.g. "
                        + "\"Check my October budgets and tell me which one went over the most.\"");
            }
            return task;
        }
    }

    private static String text(String task, AgentResponse r, Map<String, String> proposed, Map<String, String> applied,
                               MapleCfoFacade app) {
        StringBuilder b = new StringBuilder();
        b.append("Task: ").append(firstLine(task)).append('\n');
        b.append("Model: ").append(app.getLlm().modelName())
                .append(app.getLlm().isMock() ? "  (demo mode: no API key, fixed rules)" : "  (LLM API)").append("\n\n");
        b.append(r.answer()).append("\n\n");
        b.append("Tools used: ").append(r.toolsUsed().isEmpty() ? "none" : String.join(", ", r.toolsUsed())).append('\n');
        b.append("Every amount checked against the tool results: ").append(r.grounded() ? "yes" : "NO").append('\n');
        if (!proposed.isEmpty()) {
            b.append(applied.isEmpty() ? "Proposed changes (not applied; add -confirm to apply them):\n"
                    : "Changes applied (-confirm):\n");
            proposed.forEach((id, d) -> b.append("  ").append(id).append(": ")
                    .append(applied.isEmpty() ? d : applied.get(id)).append('\n'));
        }
        return b.toString().stripTrailing();
    }

    private static String json(String task, AgentResponse r, Map<String, String> proposed, Map<String, String> applied,
                               MapleCfoFacade app) throws IOException {
        ObjectNode o = JSON.createObjectNode();
        o.put("task", task);
        o.put("answer", r.answer());
        ArrayNode tools = o.putArray("toolsUsed");
        r.toolsUsed().forEach(tools::add);
        o.put("grounded", r.grounded());
        ArrayNode p = o.putArray("proposedActions");
        proposed.forEach((id, d) -> p.addObject().put("id", id).put("description", d));
        ArrayNode a = o.putArray("appliedActions");
        applied.forEach((id, d) -> a.addObject().put("id", id).put("result", d));
        o.put("demoMode", app.getLlm().isMock());
        o.put("model", app.getLlm().modelName());
        return JSON.writeValueAsString(o);
    }

    private static String firstLine(String task) {
        String first = task.lines().findFirst().orElse("");
        return first.length() > 100 ? first.substring(0, 97) + "..." : first;
    }
}
