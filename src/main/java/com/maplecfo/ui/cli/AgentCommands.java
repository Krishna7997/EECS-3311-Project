package com.maplecfo.ui.cli;

import com.maplecfo.agent.AgentResponse;
import com.maplecfo.agent.llm.LlmClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI commands for F10, the AI CFO agent. */
final class AgentCommands {

    private AgentCommands() {
    }

    @Command(name = "ask", description = "Ask the AI CFO a question, e.g. ask How much TFSA room do I have? (F10)")
    static class AskCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(arity = "1..*", description = "Your question") List<String> words;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            if (root.facade().getLlm().isMock()) {
                out.println("(Demo mode: no API key in .env, using the offline mock model.)");
            }
            AgentResponse r = root.facade().askCfo(String.join(" ", words));
            out.println(r.answer());
            if (!r.toolsUsed().isEmpty()) {
                out.println("  [tools used: " + String.join(", ", r.toolsUsed()) + "]");
            }
            if (r.hasPendingAction()) {
                out.println("  To apply this change type: confirm " + r.pendingActionId()
                        + "   (or: dismiss " + r.pendingActionId() + ")");
            }
            root.printNewAlerts(out);
            return 0;
        }
    }

    @Command(name = "confirm", description = "Apply a change the AI proposed (undoable).")
    static class ConfirmCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(description = "Action id, e.g. A1") String id;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            out.println("Done: " + root.facade().confirmProposedAction(id) + ". Type 'undo' to reverse it.");
            root.printNewAlerts(out);
            return 0;
        }
    }

    @Command(name = "dismiss", description = "Reject a change the AI proposed.")
    static class DismissCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(description = "Action id, e.g. A1") String id;

        @Override
        public Integer call() {
            boolean removed = root.facade().dismissProposedAction(id);
            spec.commandLine().getOut().println(removed ? "Dismissed " + id + ". Nothing was changed." : "No pending action " + id + ".");
            return 0;
        }
    }

    @Command(name = "ai", description = "Show which AI model is used, pending proposals and tokens used this session.")
    static class AiStatusCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            LlmClient llm = root.facade().getLlm();
            out.println("Model: " + llm.modelName() + (llm.isMock() ? "  (add LLM_API_KEY to .env to use Claude)" : ""));
            LlmClient.UsageTotals u = llm.usage();
            out.println("This session: " + u.requests() + " requests, " + u.inputTokens() + " input tokens, "
                    + u.outputTokens() + " output tokens");
            Map<String, String> pending = root.facade().getPendingActions();
            out.println(pending.isEmpty() ? "No pending proposals." : "Pending proposals:");
            pending.forEach((id, d) -> out.println("  " + id + ": " + d));
            return 0;
        }
    }

    @Command(name = "review", description = "AI monthly review: what happened, a to-do list and proposed budgets for "
            + "next month (F17). Use interactive mode to confirm the proposals.")
    static class ReviewCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Option(names = {"-m", "--month"}, description = "Month to review (default: last month)")
        java.time.YearMonth month;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            java.time.YearMonth m = month != null ? month : java.time.YearMonth.from(root.facade().today()).minusMonths(1);
            out.println(root.facade().getLlm().isMock() ? "(Demo mode: fixed steps instead of the AI.)"
                    : "Reviewing " + m + " with the AI (this takes a few seconds)...");
            com.maplecfo.agent.MonthlyReviewService.MonthlyReview r = root.facade().runMonthlyReview(m);
            out.println(r.text());
            out.println("  [tools used: " + String.join(", ", r.toolsUsed()) + "]");
            if (!r.proposals().isEmpty()) {
                out.println("Proposed changes (nothing changes until you confirm):");
                r.proposals().forEach((id, d) -> out.println("  " + id + ": " + d + "   ->  confirm " + id + "  or  dismiss " + id));
            }
            root.printNewAlerts(out);
            return 0;
        }
    }
}
