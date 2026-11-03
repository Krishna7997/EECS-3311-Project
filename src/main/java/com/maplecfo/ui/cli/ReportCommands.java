package com.maplecfo.ui.cli;

import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.reports.MonthlyReport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI command for F12, the monthly summary. */
@Command(name = "report", description = "Monthly summary with an AI-written narrative (F12).")
final class ReportCommands implements Callable<Integer> {

    @CommandLine.ParentCommand MapleCfoCli root;
    @CommandLine.Spec CommandLine.Model.CommandSpec spec;
    @Option(names = {"-m", "--month"}, description = "Month (default: last month)") YearMonth month;

    @Override
    public Integer call() {
        YearMonth m = month != null ? month : YearMonth.from(root.facade().today()).minusMonths(1);
        MonthlyReport r = root.facade().generateMonthlyReport(m);
        PrintWriter out = spec.commandLine().getOut();
        out.println("Monthly report for " + m);
        if (r.hasData()) {
            out.println("Income " + r.income() + "   Spending " + r.spending() + "   Saved " + r.saved()
                    + " (" + Math.round(r.savingsRatePercent()) + "%)");
            List<List<String>> rows = new ArrayList<>();
            r.spendingByCategory().entrySet().stream().sorted(Map.Entry.<Category, Money>comparingByValue().reversed())
                    .forEach(e -> rows.add(List.of(e.getKey().name(), e.getValue().toString())));
            out.print(root.renderer().table(List.of("Category", ">Spent"), rows));
            for (BudgetStatus s : r.budgets()) {
                out.println("Budget " + s.budget().category() + ": " + root.renderer().bar(s.percentUsed()));
            }
            out.println();
        }
        out.println(r.narrative());
        out.println(r.narrativeByAi() ? "  [summary written by " + root.facade().getLlm().modelName() + "]"
                : "  [template summary" + (root.facade().getLlm().isMock() ? ", demo mode" : "") + "]");
        return 0;
    }
}
