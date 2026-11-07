package com.maplecfo.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.agent.llm.ToolCall;
import com.maplecfo.planning.BudgetPlanner;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F17 (new in Stage 2): the AI monthly review. The agent plans a multi-step task on its own: it gathers the month's
 * figures with several tools, asks for budget suggestions, proposes next month's budgets (each one waits for the
 * user to confirm or dismiss it) and writes a short to-do list. Every amount it writes is checked against the tool
 * results. Without an AI model the same tools run in a fixed order instead.
 */
public class MonthlyReviewService {

    public static final int MAX_STEPS = 10;
    public static final int MAX_PROPOSALS = 5;
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * @param proposals proposed budget changes (action id to description), applied only after the user confirms
     * @param byAi      false when the fixed-order rules were used (demo mode)
     */
    public record MonthlyReview(YearMonth month, String text, List<String> toolsUsed, Map<String, String> proposals,
                                boolean byAi, boolean grounded) {
        public MonthlyReview {
            toolsUsed = List.copyOf(toolsUsed);
            proposals = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(proposals));
        }
    }

    private final CfoAgent agent;
    private final ToolRegistry tools;
    private final BudgetPlanner planner;

    public MonthlyReviewService(CfoAgent agent, ToolRegistry tools, BudgetPlanner planner) {
        this.agent = agent;
        this.tools = tools;
        this.planner = planner;
    }

    public MonthlyReview review(YearMonth month) {
        YearMonth next = month.plusMonths(1);
        LlmClient llm = agent.getLlm();
        if (llm.isMock()) {
            return byRules(month, next);
        }
        AgentResponse r = agent.runTask(instruction(month, next), MAX_STEPS);
        return new MonthlyReview(month, r.answer(), r.toolsUsed(), describe(r.pendingActionIds()), true, r.grounded());
    }

    static String instruction(YearMonth month, YearMonth next) {
        return """
                Do my monthly money review for %1$s. Work in these steps:
                1. Find out what happened: call spending_summary and budget_status for %1$s, recurring_charges,
                   goal_forecast, and contribution_room for TFSA.
                2. Call suggest_budgets for %2$s.
                3. For at most %3$d categories whose suggested budget differs from the current one (biggest spending
                   first), call propose_action with action set_budget, that category, the suggested amount and month
                   %2$s. Propose nothing else.
                4. Answer with one sentence on how %1$s went, then 3 to 5 bullet points starting with "- " of concrete
                   things to do this month (budgets that went over, price increases that already happened, goals that
                   are behind, unused room), and a last line saying the proposed budgets are waiting for my
                   confirmation. A next_charge_date is only when the next charge is due; it is not a price change.
                """.formatted(month, next, MAX_PROPOSALS);
    }

    /** Demo mode: the same tools in a fixed order, and their own summaries as the review. */
    private MonthlyReview byRules(YearMonth month, YearMonth next) {
        List<String> used = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        run("spending_summary", args("month", month.toString()), used, lines);
        run("budget_status", args("month", month.toString()), used, lines);
        run("recurring_charges", args(), used, lines);
        run("goal_forecast", args(), used, lines);
        run("contribution_room", args("account", "TFSA"), used, lines);

        List<String> ids = new ArrayList<>();
        int proposed = 0;
        for (BudgetPlanner.Suggestion s : planner.suggest(next)) {
            if (proposed == MAX_PROPOSALS) break;
            if (!s.changesBudget()) continue;
            ObjectNode a = args("action", "set_budget");
            a.put("category", s.category().name());
            a.put("amount", s.suggested().getAmount());
            a.put("month", next.toString());
            ToolResult r = tools.execute(new ToolCall("review_" + used.size(), "propose_action", a));
            used.add("propose_action");
            if (r.success()) {
                ids.add(r.data().get("action_id").asText());
                proposed++;
            }
        }
        StringBuilder text = new StringBuilder("Review of " + month + " (demo mode: fixed steps, no AI wording):\n");
        lines.forEach(l -> text.append("- ").append(l).append('\n'));
        text.append(ids.isEmpty() ? "No budget changes to suggest for " + next + "."
                : ids.size() + " budgets for " + next + " are proposed, based on your last three months. "
                + "Confirm the ones you agree with.");
        return new MonthlyReview(month, text.toString(), used.stream().distinct().toList(), describe(ids), false, true);
    }

    private void run(String tool, ObjectNode a, List<String> used, List<String> lines) {
        ToolResult r = tools.execute(new ToolCall("review_" + used.size(), tool, a));
        used.add(tool);
        if (r.success() && r.data().hasNonNull("summary")) {
            lines.add(r.data().get("summary").asText());
        }
    }

    private Map<String, String> describe(List<String> ids) {
        Map<String, String> all = agent.getPendingActions().list();
        Map<String, String> out = new LinkedHashMap<>();
        for (String id : ids) {
            if (all.containsKey(id)) out.put(id, all.get(id));
        }
        return out;
    }

    private static ObjectNode args(String... kv) {
        ObjectNode a = JSON.createObjectNode();
        for (int i = 0; i + 1 < kv.length; i += 2) a.put(kv[i], kv[i + 1]);
        return a;
    }
}
