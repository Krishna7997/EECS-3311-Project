package com.maplecfo.ui.cli;

import com.maplecfo.app.AppContext;
import com.maplecfo.automation.ImportFolderWatcher;
import com.maplecfo.planning.RrspRoomEstimator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/** CLI commands for F13 (auto import, reminders) and F14 (RRSP room estimate). */
final class AutomationCommands {

    private AutomationCommands() {
    }

    @Command(name = "watch", description = "Import bank CSVs dropped into a folder (F13). Default folder: ~/.maplecfo/inbox")
    static class WatchCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Option(names = "--folder", description = "Folder to watch") Path folder;
        @Option(names = {"-a", "--account"}, defaultValue = "chequing",
                description = "Account when the file name does not mention one (default: chequing)") String account;
        @Option(names = "--once", description = "Import what is there now and stop") boolean once;

        @Override
        public Integer call() throws Exception {
            PrintWriter out = spec.commandLine().getOut();
            Path f = folder != null ? folder : root.facade().getImportFolder();
            ImportFolderWatcher w = root.facade().newImportWatcher(f, account);
            if (once) {
                List<ImportFolderWatcher.Outcome> done = w.scanOnce();
                if (done.isEmpty()) out.println("No new CSV files in " + f);
                done.forEach(o -> out.println(o.message()));
                root.printNewAlerts(out);
                return 0;
            }
            out.println("Watching " + f + " for new bank CSV files. Press Ctrl+C to stop.");
            out.flush();
            root.facade().subscribe(com.maplecfo.events.EventType.AUTO_IMPORTED, e -> {
                root.printNewAlerts(out);
                out.flush();
            });
            w.start();
            root.printNewAlerts(out);
            out.flush();
            Thread.currentThread().join(); // until Ctrl+C
            return 0;
        }
    }

    @Command(name = "inbox", description = "Show or change the folder watched for bank CSVs (F13).")
    static class InboxCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(arity = "0..1", description = "New folder (leave out to show the current one)") Path folder;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            if (folder != null) {
                if (root.facade().isImportFolderFromEnvironment()) {
                    out.println("Note: MAPLECFO_INBOX is set, so it is used instead of the saved folder.");
                }
                out.println("Import folder saved: " + root.facade().setImportFolder(folder));
            } else {
                out.println("Import folder: " + root.facade().getImportFolder()
                        + (root.facade().isImportFolderFromEnvironment() ? "  (from MAPLECFO_INBOX)" : ""));
                out.println("Change it with: inbox <folder>");
            }
            return 0;
        }
    }

    @Command(name = "checkin", description = "Your monthly check-in list (F13). Use --done when finished.")
    static class CheckinCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Option(names = "--done", description = "Mark this month's check-in as done") boolean done;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            if (done) {
                root.facade().completeMonthlyCheckin();
                out.println("Check-in done for this month. See you next month!");
                return 0;
            }
            out.println(root.facade().isMonthlyCheckinDue() ? "Your monthly check-in is due:" : "This month's check-in is done. The list, for reference:");
            out.println("  1. Import last month's CSV from each bank:   import <file> -a <account>   (or save it in: "
                    + root.facade().getImportFolder() + ")");
            out.println("  2. Update balances that are not imported:     account update <account> <balance>");
            out.println("  3. Look over your budgets:                    budget status");
            out.println("  4. Confirm detected contributions, check room: ai   then   room");
            out.println("Then run: checkin --done");
            return 0;
        }
    }

    @Command(name = "reminders", description = "Show reminders that are due (F13).")
    static class RemindersCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            List<String> due = root.facade().checkReminders();
            PrintWriter out = spec.commandLine().getOut();
            if (due.isEmpty()) out.println("Nothing due.");
            root.printNewAlerts(out);
            return 0;
        }
    }

    @Command(name = "rrsp-estimate", description = "Estimate RRSP room from your pay deposits (F14).")
    static class RrspEstimateCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Option(names = {"-y", "--year"}, description = "Year the room is for (default: this year, based on last year's pay)") Integer year;
        @Option(names = "--save", description = "Save the estimate as your RRSP deduction limit") boolean save;

        @Override
        public Integer call() {
            int y = year != null ? year : root.facade().today().getYear();
            RrspRoomEstimator.Estimate e = root.facade().estimateRrspRoom(y);
            PrintWriter out = spec.commandLine().getOut();
            if (e.status() == RrspRoomEstimator.Status.UNKNOWN) {
                out.println("RRSP room for " + y + ": unknown. " + e.explanation());
                return 0;
            }
            out.println("Estimated RRSP room for " + y + ": " + e.estimate());
            out.println(e.explanation());
            if (save) {
                var p = root.facade().getProfile();
                p.setRrspDeductionLimit(e.estimate());
                root.facade().saveProfile(p);
                out.println("Saved as your RRSP deduction limit. Replace it with the number from your CRA notice when you have it.");
            }
            return 0;
        }
    }

    @Command(name = "limits", description = "Show the contribution limits in use, or check for newer ones (F15).")
    static class LimitsCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(arity = "0..1", description = "refresh") String action;
        @Option(names = "--force", description = "Check now even if checked this week") boolean force;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            if ("refresh".equalsIgnoreCase(action)) {
                var s = root.facade().refreshLimits(force);
                out.println(s.state() + ": " + s.message());
            } else if (action != null) {
                throw new IllegalArgumentException("Unknown action '" + action + "'. Try: limits refresh");
            }
            out.println(root.facade().describeLimits());
            root.printNewAlerts(out);
            return 0;
        }
    }
}
