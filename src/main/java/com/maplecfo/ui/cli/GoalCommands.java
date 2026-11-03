package com.maplecfo.ui.cli;

import com.maplecfo.domain.Money;
import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.planning.GoalForecast;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** CLI commands for F09 savings goals. */
@Command(name = "goal", description = "Savings goals and whether you are on track (F09).",
        subcommands = {GoalCommands.AddCmd.class, GoalCommands.ListCmd.class, GoalCommands.DepositCmd.class,
                GoalCommands.ForecastCmd.class, GoalCommands.RemoveCmd.class})
final class GoalCommands {

    @CommandLine.ParentCommand MapleCfoCli root;

    static SavingsGoal find(MapleCfoCli root, String idOrName) {
        return root.facade().getGoals().stream()
                .filter(g -> g.getId().equalsIgnoreCase(idOrName) || g.getName().equalsIgnoreCase(idOrName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No goal '" + idOrName + "'. See: goal list"));
    }

    @Command(name = "add", description = "Add a goal (undoable), e.g. goal add \"Emergency fund\" 5000 --by 2027-06-30")
    static class AddCmd implements Callable<Integer> {
        @CommandLine.ParentCommand GoalCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(index = "0", description = "Name") String name;
        @Parameters(index = "1", description = "Target amount") Money target;
        @Option(names = "--by", description = "Deadline, YYYY-MM-DD") LocalDate deadline;
        @Option(names = "--saved", defaultValue = "0", description = "Already saved") Money saved;
        @Option(names = "--account", description = "Linked account id") String account;

        @Override
        public Integer call() {
            String id = AccountCommands.slug(name);
            SavingsGoal g = new SavingsGoal(id, name, target, saved, deadline, account);
            parent.root.facade().createGoal(g);
            PrintWriter out = spec.commandLine().getOut();
            out.println("Goal added (id " + id + ").");
            out.println(parent.root.facade().forecastGoal(id).summary());
            return 0;
        }
    }

    @Command(name = "list", description = "List goals with progress.")
    static class ListCmd implements Callable<Integer> {
        @CommandLine.ParentCommand GoalCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            List<GoalForecast> all = parent.root.facade().forecastGoals();
            PrintWriter out = spec.commandLine().getOut();
            if (all.isEmpty()) {
                out.println("No goals yet. Try: goal add \"Emergency fund\" 5000 --by 2027-06-30");
                return 0;
            }
            CliRenderer r = parent.root.renderer();
            List<List<String>> rows = new ArrayList<>();
            for (GoalForecast f : all) {
                SavingsGoal g = f.goal();
                rows.add(List.of(g.getId(), g.getName(), g.getSaved() + " / " + g.getTarget(), r.bar(g.progress() * 100),
                        g.getDeadline() == null ? "-" : g.getDeadline().toString(),
                        f.isComplete() ? "done" : f.onTrack() ? "on track" : "behind"));
            }
            out.print(r.table(List.of("Id", "Name", ">Saved", "Progress", "Deadline", "Status"), rows));
            return 0;
        }
    }

    @Command(name = "deposit", description = "Record money put towards a goal, e.g. goal deposit emergency-fund 200")
    static class DepositCmd implements Callable<Integer> {
        @CommandLine.ParentCommand GoalCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(index = "0", description = "Goal id or name") String idOrName;
        @Parameters(index = "1", description = "Amount") Money amount;

        @Override
        public Integer call() {
            SavingsGoal g = parent.root.facade().recordGoalDeposit(find(parent.root, idOrName).getId(), amount);
            PrintWriter out = spec.commandLine().getOut();
            out.println(g.getName() + ": " + g.getSaved() + " of " + g.getTarget() + " saved.");
            parent.root.printNewAlerts(out);
            return 0;
        }
    }

    @Command(name = "forecast", description = "Will you reach the goal in time?")
    static class ForecastCmd implements Callable<Integer> {
        @CommandLine.ParentCommand GoalCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(description = "Goal id or name") String idOrName;

        @Override
        public Integer call() {
            spec.commandLine().getOut().println(
                    parent.root.facade().forecastGoal(find(parent.root, idOrName).getId()).summary());
            return 0;
        }
    }

    @Command(name = "remove", description = "Delete a goal.")
    static class RemoveCmd implements Callable<Integer> {
        @CommandLine.ParentCommand GoalCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(description = "Goal id or name") String idOrName;

        @Override
        public Integer call() {
            SavingsGoal g = find(parent.root, idOrName);
            parent.root.facade().deleteGoal(g.getId());
            spec.commandLine().getOut().println("Removed goal " + g.getName() + ".");
            return 0;
        }
    }
}
