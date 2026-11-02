package com.maplecfo.ui.cli;

import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** CLI commands for F04 monthly budgets. */
@Command(name = "budget", description = "Set monthly budgets and see spending against them (F04).",
        subcommands = {BudgetCommands.SetCmd.class, BudgetCommands.StatusCmd.class})
final class BudgetCommands {

    @CommandLine.ParentCommand MapleCfoCli root;

    @Command(name = "set", description = "Set a monthly limit for a category (undoable).")
    static class SetCmd implements Callable<Integer> {
        @CommandLine.ParentCommand BudgetCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(index = "0", description = "Category: ${COMPLETION-CANDIDATES}") Category category;
        @Parameters(index = "1", description = "Monthly limit, e.g. 250") Money limit;
        @Option(names = {"-m", "--month"}, description = "Month (default: this month)") YearMonth month;

        @Override
        public Integer call() {
            YearMonth m = month != null ? month : YearMonth.from(parent.root.facade().today());
            parent.root.facade().setBudget(category, limit, m);
            PrintWriter out = spec.commandLine().getOut();
            out.println("Budget set: " + category + " " + limit + " for " + m + ".");
            parent.root.printNewAlerts(out);
            return 0;
        }
    }

    @Command(name = "status", description = "Show spending against each budget.")
    static class StatusCmd implements Callable<Integer> {
        @CommandLine.ParentCommand BudgetCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Option(names = {"-m", "--month"}, description = "Month (default: this month)") YearMonth month;

        @Override
        public Integer call() {
            YearMonth m = month != null ? month : YearMonth.from(parent.root.facade().today());
            List<BudgetStatus> statuses = parent.root.facade().getBudgetStatus(m);
            PrintWriter out = spec.commandLine().getOut();
            if (statuses.isEmpty()) {
                out.println("No budgets for " + m + ". Try: budget set DINING 250");
                return 0;
            }
            CliRenderer r = parent.root.renderer();
            List<List<String>> rows = new ArrayList<>();
            for (BudgetStatus s : statuses) {
                rows.add(List.of(s.budget().category().name(), s.spent().toString(), s.budget().limit().toString(),
                        s.remaining().toString(), r.bar(s.percentUsed())));
            }
            out.println("Budgets for " + m);
            out.print(r.table(List.of("Category", ">Spent", ">Limit", ">Left", "Used"), rows));
            return 0;
        }
    }
}
