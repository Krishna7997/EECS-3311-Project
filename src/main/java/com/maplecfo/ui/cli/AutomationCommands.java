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
            Path f = folder != null ? folder : AppContext.defaultImportFolder();
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
}
