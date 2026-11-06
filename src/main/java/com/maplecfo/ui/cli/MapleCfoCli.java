package com.maplecfo.ui.cli;

import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Money;
import com.maplecfo.events.Alert;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The MapleCFO command-line interface (picocli). Run with no arguments for an
 * interactive session, where undo and redo work across commands.
 */
@Command(name = "maplecfo", mixinStandardHelpOptions = true, version = "MapleCFO 0.7",
        description = "MapleCFO: your AI money CFO for Canada.",
        subcommands = {
                TransactionCommands.ImportCmd.class,
                TransactionCommands.ProfilesCmd.class,
                TransactionCommands.LearnFormatCmd.class,
                TransactionCommands.TxCmd.class,
                MapleCfoCli.UndoCmd.class,
                MapleCfoCli.RedoCmd.class,
                BudgetCommands.class,
                RoomCommands.class,
                RoomCommands.ProfileCmd.class,
                AccountCommands.AccountCmd.class,
                AccountCommands.NetWorthCmd.class,
                GoalCommands.class,
                AccountCommands.AffordCmd.class,
                PlanningCommands.FireCmd.class,
                PlanningCommands.DebtCmd.class,
                PlanningCommands.RecurringCmd.class,
                MapleCfoCli.AlertsCmd.class,
                ReportCommands.class,
                AgentCommands.AskCmd.class,
                AgentCommands.ConfirmCmd.class,
                AgentCommands.DismissCmd.class,
                AgentCommands.AiStatusCmd.class,
                AutomationCommands.WatchCmd.class,
                AutomationCommands.RemindersCmd.class,
                AutomationCommands.InboxCmd.class,
                AutomationCommands.CheckinCmd.class,
                AutomationCommands.RrspEstimateCmd.class,
                AutomationCommands.LimitsCmd.class,
                MapleCfoCli.GuiCmd.class,
                CommandLine.HelpCommand.class
        })
public class MapleCfoCli implements Callable<Integer> {

    @Option(names = "--db", description = "Database file (default: ~/.maplecfo/maplecfo.db)")
    Path databaseFile;

    private MapleCfoFacade facade;
    private final CliRenderer renderer = new CliRenderer();
    private int alertsShown;
    private boolean interactive;
    private boolean confirmHintShown;

    public static void main(String[] args) {
        CommandLine cl = newCommandLine(new MapleCfoCli());
        int code = cl.execute(args);
        cl.getOut().flush();
        cl.getErr().flush();
        System.exit(code);
    }

    static CommandLine newCommandLine(MapleCfoCli root) {
        CommandLine cl = new CommandLine(root);
        cl.setCaseInsensitiveEnumValuesAllowed(true);
        cl.registerConverter(Money.class, MapleCfoCli::parseMoney);
        cl.registerConverter(YearMonth.class, YearMonth::parse);
        cl.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            commandLine.getErr().println("Error: " + friendly(ex));
            return 1;
        });
        return cl;
    }

    static Money parseMoney(String text) {
        try {
            return Money.of(text.replace("$", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new CommandLine.TypeConversionException("'" + text + "' is not an amount");
        }
    }

    private static String friendly(Exception ex) {
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? ex.getClass().getSimpleName() : msg;
    }

    /** The facade, created on first use so "--help" works without a database. */
    MapleCfoFacade facade() {
        if (facade == null) {
            facade = AppContext.create(databaseFile != null ? databaseFile : AppContext.defaultDatabasePath());
        }
        return facade;
    }

    /** Lets tests run the CLI against an in-memory or temporary setup. */
    void useFacade(MapleCfoFacade f) {
        this.facade = f;
    }

    CliRenderer renderer() {
        return renderer;
    }

    /** Prints alerts raised since the last call (e.g. a budget exceeded by an import). */
    void printNewAlerts(PrintWriter out) {
        List<Alert> all = facade().getAlerts();
        for (int i = alertsShown; i < all.size(); i++) {
            Alert a = all.get(i);
            out.println("[" + a.severity() + "] " + a.message());
            if (!interactive && !confirmHintShown && a.source() == com.maplecfo.events.EventType.CONTRIBUTION_DETECTED) {
                confirmHintShown = true;
                out.println("  (Proposals are kept while MapleCFO is open: run maplecfo with no arguments, "
                        + "or use the GUI, to confirm them.)");
            }
        }
        alertsShown = all.size();
    }

    /** No sub-command: start an interactive session. */
    @Override
    public Integer call() throws IOException {
        CommandLine cl = newCommandLine(this);
        PrintWriter out = cl.getOut();
        interactive = true;
        out.println("MapleCFO interactive mode. Type 'help' for commands, 'exit' to quit.");
        facade().checkReminders();
        printNewAlerts(out);
        out.flush();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while (true) {
            out.print("maplecfo> ");
            out.flush();
            line = in.readLine();
            if (line == null || line.trim().equalsIgnoreCase("exit") || line.trim().equalsIgnoreCase("quit")) {
                break;
            }
            List<String> args = splitArgs(line);
            if (!args.isEmpty()) {
                cl.execute(args.toArray(String[]::new));
                out.flush();
                cl.getErr().flush();
            }
        }
        return 0;
    }

    /** Splits a typed line into arguments, keeping "quoted text" together. */
    static List<String> splitArgs(String line) {
        List<String> args = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (any) {
                    args.add(cur.toString());
                    cur.setLength(0);
                    any = false;
                }
            } else {
                cur.append(c);
                any = true;
            }
        }
        if (any) {
            args.add(cur.toString());
        }
        return args;
    }

    @Command(name = "undo", description = "Undo the last edit (budget, category, goal or confirmed AI change).")
    static class UndoCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            String what = root.facade().describeUndo();
            out.println(root.facade().undoLastEdit() ? "Undone: " + what : "Nothing to undo.");
            return 0;
        }
    }

    @Command(name = "redo", description = "Redo the last undone edit.")
    static class RedoCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            spec.commandLine().getOut().println(root.facade().redoLastEdit() ? "Redone." : "Nothing to redo.");
            return 0;
        }
    }

    @Command(name = "alerts", description = "Show budget and contribution alerts from this session.")
    static class AlertsCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            List<Alert> all = root.facade().getAlerts();
            if (all.isEmpty()) {
                out.println("No alerts.");
            }
            all.forEach(a -> out.println("[" + a.severity() + "] " + a.message()));
            root.alertsShown = all.size();
            return 0;
        }
    }

    @Command(name = "gui", description = "Open the MapleCFO window (JavaFX).")
    static class GuiCmd implements Callable<Integer> {
        @Override
        public Integer call() {
            com.maplecfo.ui.gui.GuiLauncher.main(new String[0]);
            return 0;
        }
    }
}
