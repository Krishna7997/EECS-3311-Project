package com.maplecfo.ui.cli;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.Transaction;
import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.ImportResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** CLI commands for F01 (import) and F02 (categories). */
final class TransactionCommands {

    private TransactionCommands() {
    }

    @Command(name = "import", description = "Import a bank or card CSV export (F01).")
    static class ImportCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Parameters(index = "0", description = "CSV file") Path file;
        @Option(names = {"-a", "--account"}, required = true, description = "Account id, e.g. chequing") String account;
        @Option(names = {"-p", "--profile"},
                description = "Bank format (see: maplecfo profiles). Leave it out to detect it automatically.")
        String profile;

        @Override
        public Integer call() throws Exception {
            PrintWriter out = spec.commandLine().getOut();
            String format = profile;
            if (format == null || format.isBlank()) {
                format = root.facade().detectImportFormat(file).orElseThrow(() ->
                        new com.maplecfo.importer.MappingMismatchException("Could not recognise this file's columns. "
                                + "Pick the format with -p (see: maplecfo profiles), or let the AI read it: "
                                + "learn-format " + file + " -a " + account));
                out.println("Detected format: " + format);
            }
            ImportResult r = root.facade().importTransactions(file, account, format);
            out.println("Imported " + r.imported() + " transactions into '" + account + "'.");
            out.println("Duplicates skipped: " + r.duplicatesSkipped() + "   Need review: " + r.uncategorized());
            if (!r.rejectedRows().isEmpty()) {
                out.println("Rejected rows (" + r.rejectedRows().size() + "):");
                r.rejectedRows().forEach(row -> out.println("  " + row));
            }
            r.latestBalanceIfKnown().ifPresent(b -> out.println("Latest balance in file: " + b));
            root.printNewAlerts(out);
            return 0;
        }
    }

    @Command(name = "learn-format", description = "Let the AI work out a bank format MapleCFO doesn't know (F16). "
            + "Shows a preview; add --save to keep it (and import the file when -a is given).")
    static class LearnFormatCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Parameters(index = "0", description = "CSV file") Path file;
        @Option(names = "--name", description = "Name for the new format, e.g. Tangerine") String name;
        @Option(names = "--save", description = "Save the format (only after checking the preview)") boolean save;
        @Option(names = {"-a", "--account"}, description = "With --save: also import the file into this account") String account;

        @Override
        public Integer call() throws Exception {
            PrintWriter out = spec.commandLine().getOut();
            com.maplecfo.importer.AiFormatReader.Proposal p = root.facade().proposeImportFormat(file, name);
            out.println("AI suggestion for " + file.getFileName() + " (format \"" + p.mapping().profileName() + "\"):");
            if (!p.explanation().isBlank()) out.println("  " + p.explanation());
            out.println("  Reads " + p.describeColumns());
            out.println("  Test read: " + p.rowsRead() + " rows OK, " + p.rowsRejected().size() + " rejected");
            p.rowsRejected().stream().limit(5).forEach(r -> out.println("    " + r));
            List<List<String>> rows = new ArrayList<>();
            p.preview().forEach(t -> rows.add(List.of(t.getDate().toString(), t.getDescription(), t.getAmount().toString())));
            out.print(root.renderer().table(List.of("Date", "Description", "Amount"), rows));
            if (!p.looksRight()) {
                out.println("This does not look right, so it was not saved. Choose the format with -p instead.");
                return 1;
            }
            if (!save) {
                out.println("Looks right? Run again with --save" + (account == null ? " -a <account>" : " -a " + account)
                        + " to keep this format and import the file.");
                return 0;
            }
            if (account == null) {
                root.facade().saveMappingProfile(p.mapping());
                out.println("Saved format \"" + p.mapping().profileName() + "\". Files like this are now detected automatically.");
            } else {
                ImportResult r = root.facade().saveFormatAndImport(p, file, account);
                out.println("Saved format \"" + p.mapping().profileName() + "\" and imported " + r.imported()
                        + " transactions into '" + account + "'.");
                root.printNewAlerts(out);
            }
            return 0;
        }
    }

    @Command(name = "profiles", description = "List the saved CSV mapping profiles.")
    static class ProfilesCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            List<List<String>> rows = new ArrayList<>();
            for (ColumnMapping m : root.facade().getMappingProfiles()) {
                String amount = m.usesSingleAmount() ? m.amountColumn() : m.debitColumn() + " / " + m.creditColumn();
                rows.add(List.of(m.profileName(), m.hasHeader() ? "yes" : "no", m.dateColumn() + " (" + m.dateFormat() + ")",
                        m.descriptionColumn(), amount));
            }
            spec.commandLine().getOut().print(root.renderer().table(
                    List.of("Profile", "Header", "Date column", "Description", "Amount"), rows));
            return 0;
        }
    }

    @Command(name = "tx", description = "List, search and correct transactions (F02).",
            subcommands = {TxCmd.ListCmd.class, TxCmd.SearchCmd.class, TxCmd.RecategorizeCmd.class})
    static class TxCmd {
        @CommandLine.ParentCommand MapleCfoCli root;

        static void print(MapleCfoCli root, PrintWriter out, List<Transaction> txs, int limit) {
            CliRenderer r = root.renderer();
            List<List<String>> rows = new ArrayList<>();
            for (Transaction t : txs.subList(0, Math.min(limit, txs.size()))) {
                rows.add(List.of(r.shortId(t.getId()), t.getDate().toString(), r.truncate(t.getDescription(), 34),
                        t.getAmount().toString(), t.getCategory().name(), t.getCategorySource().name()));
            }
            out.print(r.table(List.of("Id", "Date", "Description", ">Amount", "Category", "Source"), rows));
            if (txs.size() > limit) {
                out.println("... " + (txs.size() - limit) + " more (use --limit)");
            }
        }

        @Command(name = "list", description = "List transactions, newest first.")
        static class ListCmd implements Callable<Integer> {
            @CommandLine.ParentCommand TxCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Option(names = {"-m", "--month"}, description = "Month, e.g. 2026-10") YearMonth month;
            @Option(names = "--review", description = "Only transactions that need a category") boolean review;
            @Option(names = "--limit", defaultValue = "25") int limit;
            @Option(names = "--from", description = "From date, e.g. 2026-09-01 (instead of a month)") java.time.LocalDate from;
            @Option(names = "--to", description = "To date") java.time.LocalDate to;
            @Option(names = {"-a", "--account"}, description = "Only this account") String account;
            @Option(names = {"-c", "--category"}, description = "Only this category: ${COMPLETION-CANDIDATES}") com.maplecfo.domain.Category category;
            @Option(names = "--in", description = "Only money in") boolean in;
            @Option(names = "--out", description = "Only money out") boolean out;
            @Option(names = "--min", description = "Smallest amount") com.maplecfo.domain.Money min;
            @Option(names = "--max", description = "Largest amount") com.maplecfo.domain.Money max;

            @Override
            public Integer call() {
                com.maplecfo.services.TransactionFilter f = com.maplecfo.services.TransactionFilter.all()
                        .account(account).category(category).amounts(min, max)
                        .direction(in ? com.maplecfo.services.TransactionFilter.Direction.IN
                                : out ? com.maplecfo.services.TransactionFilter.Direction.OUT : null);
                List<Transaction> txs;
                if (review) {
                    txs = f.apply(parent.root.facade().getTransactionsNeedingReview());
                } else if (from != null || to != null) {
                    txs = parent.root.facade().findTransactions(f.between(from, to));
                } else if (month != null) {
                    txs = parent.root.facade().findTransactions(f.between(month.atDay(1), month.atEndOfMonth()));
                } else {
                    txs = parent.root.facade().findTransactions(f);
                }
                java.io.PrintWriter pw = spec.commandLine().getOut();
                print(parent.root, pw, txs, limit);
                com.maplecfo.domain.Money[] t = com.maplecfo.services.TransactionFilter.totals(txs);
                pw.println(txs.size() + " transactions  ·  money in " + t[0] + "  ·  money out " + t[1]);
                return 0;
            }
        }

        @Command(name = "search", description = "Find transactions whose description contains the text.")
        static class SearchCmd implements Callable<Integer> {
            @CommandLine.ParentCommand TxCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Parameters(description = "Text to look for") String query;
            @Option(names = "--limit", defaultValue = "25") int limit;

            @Override
            public Integer call() {
                print(parent.root, spec.commandLine().getOut(), parent.root.facade().searchTransactions(query), limit);
                return 0;
            }
        }

        @Command(name = "recategorize", description = "Change a transaction's category (undoable; learns a rule).")
        static class RecategorizeCmd implements Callable<Integer> {
            @CommandLine.ParentCommand TxCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Parameters(index = "0", description = "Transaction id (the first characters are enough)") String id;
            @Parameters(index = "1", description = "New category: ${COMPLETION-CANDIDATES}") Category category;

            @Override
            public Integer call() {
                parent.root.facade().recategorize(id, category);
                PrintWriter out = spec.commandLine().getOut();
                out.println("Category changed to " + category + ". Future transactions from this merchant will use it too.");
                parent.root.printNewAlerts(out);
                return 0;
            }
        }
    }
}
