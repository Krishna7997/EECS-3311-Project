package com.maplecfo.ui.cli;

import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.AssetComponent;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.NetWorthSnapshot;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI commands for F03 accounts and net worth. */
final class AccountCommands {

    private AccountCommands() {
    }

    /** Turns "TD Chequing" into "td-chequing". */
    static String slug(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "item" : s;
    }

    /** Finds an account by id or (case-insensitive) name. */
    static Account findAccount(MapleCfoCli root, String idOrName) {
        return root.facade().getAccounts().stream()
                .filter(a -> a.getId().equalsIgnoreCase(idOrName) || a.getName().equalsIgnoreCase(idOrName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No account '" + idOrName + "'. See: account list"));
    }

    @Command(name = "account", description = "Add and update accounts for net worth (F03).",
            subcommands = {AccountCmd.AddCmd.class, AccountCmd.ListCmd.class, AccountCmd.UpdateCmd.class,
                    AccountCmd.RemoveCmd.class})
    static class AccountCmd {
        @CommandLine.ParentCommand MapleCfoCli root;

        @Command(name = "add", description = "Add an account, e.g. account add \"TD Chequing\" CHEQUING 2500 --bank TD")
        static class AddCmd implements Callable<Integer> {
            @CommandLine.ParentCommand AccountCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Parameters(index = "0", description = "Name") String name;
            @Parameters(index = "1", description = "Type: ${COMPLETION-CANDIDATES}") AccountType type;
            @Parameters(index = "2", description = "Balance (for a credit card or loan: the amount owed)") Money balance;
            @Option(names = "--bank", defaultValue = "", description = "Institution") String bank;
            @Option(names = "--id", description = "Account id (default: made from the name)") String id;

            @Override
            public Integer call() {
                String accountId = id != null ? id : slug(name);
                Account a = new Account(accountId, name, type, bank.isBlank() ? "-" : bank, balance);
                List<String> warnings = parent.root.facade().addAccount(a);
                PrintWriter out = spec.commandLine().getOut();
                warnings.forEach(w -> out.println("Warning: " + w));
                out.println("Added " + name + " (id " + accountId + "). Net worth is now "
                        + parent.root.facade().getNetWorth().total() + ".");
                return 0;
            }
        }

        @Command(name = "list", description = "List accounts.")
        static class ListCmd implements Callable<Integer> {
            @CommandLine.ParentCommand AccountCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;

            @Override
            public Integer call() {
                List<Account> accounts = parent.root.facade().getAccounts();
                PrintWriter out = spec.commandLine().getOut();
                if (accounts.isEmpty()) {
                    out.println("Add your first account, e.g. account add \"TD Chequing\" CHEQUING 2500");
                    return 0;
                }
                List<List<String>> rows = new ArrayList<>();
                for (Account a : accounts) {
                    rows.add(List.of(a.getId(), a.getName(), a.getType().name(), a.getInstitution(),
                            (a.isLiability() ? "-" : "") + a.getBalance()));
                }
                out.print(parent.root.renderer().table(List.of("Id", "Name", "Type", "Bank", ">Balance"), rows));
                return 0;
            }
        }

        @Command(name = "update", description = "Set a new balance, e.g. account update td-chequing 2750")
        static class UpdateCmd implements Callable<Integer> {
            @CommandLine.ParentCommand AccountCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Parameters(index = "0", description = "Account id or name") String idOrName;
            @Parameters(index = "1", description = "New balance") Money balance;

            @Override
            public Integer call() {
                Account a = findAccount(parent.root, idOrName);
                parent.root.facade().updateAccountBalance(a.getId(), balance);
                spec.commandLine().getOut().println("Updated " + a.getName() + " to " + balance + ". Net worth is now "
                        + parent.root.facade().getNetWorth().total() + ".");
                return 0;
            }
        }

        @Command(name = "remove", description = "Remove an account.")
        static class RemoveCmd implements Callable<Integer> {
            @CommandLine.ParentCommand AccountCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Parameters(description = "Account id or name") String idOrName;

            @Override
            public Integer call() {
                Account a = findAccount(parent.root, idOrName);
                parent.root.facade().removeAccount(a.getId());
                spec.commandLine().getOut().println("Removed " + a.getName() + ".");
                return 0;
            }
        }
    }

    @Command(name = "networth", description = "Show net worth by group and its history (F03).")
    static class NetWorthCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            if (root.facade().getAccounts().isEmpty()) {
                out.println("Add your first account, e.g. account add \"TD Chequing\" CHEQUING 2500");
                return 0;
            }
            NetWorthSnapshot s = root.facade().getNetWorth();
            out.println("Net worth: " + s.total() + "   (assets " + s.assets() + ", debts " + s.liabilities() + ")");
            List<List<String>> rows = new ArrayList<>();
            for (AssetComponent g : root.facade().getAccountTree().getChildren()) {
                rows.add(List.of(g.getName(), "", g.getValue().toString()));
                for (AssetComponent a : g.getChildren()) {
                    rows.add(List.of("", a.getName(), a.getValue().toString()));
                }
            }
            out.print(root.renderer().table(List.of("Group", "Account", ">Value"), rows));
            Map<LocalDate, Money> history = root.facade().getNetWorthHistory();
            if (history.size() > 1) {
                out.println("History:");
                history.forEach((d, v) -> out.println("  " + d + "  " + v));
            }
            return 0;
        }
    }

    @Command(name = "afford", description = "Can I afford it? e.g. afford 1500 --by 2027-03-31 (F11).")
    static class AffordCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(description = "Purchase amount") Money amount;
        @Option(names = "--by", description = "Date, YYYY-MM-DD (default: 3 months from today)") LocalDate by;

        @Override
        public Integer call() {
            LocalDate date = by != null ? by : root.facade().today().plusMonths(3);
            com.maplecfo.planning.AffordabilityResult r = root.facade().checkAffordability(amount, date);
            PrintWriter out = spec.commandLine().getOut();
            out.println("Verdict: " + r.verdict());
            out.println(r.summary());
            return 0;
        }
    }

    @Command(name = "extra", description = "Where could extra money go? e.g. extra 2000 (F19). Education only, not advice.")
    static class ExtraCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(description = "The extra amount") Money amount;
        @Option(names = "--no-ai", description = "Only the numbers, without the AI explanation") boolean noAi;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            com.maplecfo.planning.ExtraMoneyPlanner.Plan p = root.facade().planExtraMoney(amount);
            List<List<String>> rows = new ArrayList<>();
            int n = 1;
            for (com.maplecfo.planning.ExtraMoneyPlanner.Step s : p.steps()) {
                rows.add(List.of(String.valueOf(n++), s.target(), s.amount().toString()));
            }
            out.println("A common order for " + p.amount() + " (education, not advice):");
            out.print(root.renderer().table(List.of("#", "Where", ">Amount"), rows));
            if (p.leftover().isPositive()) out.println("Left over: " + p.leftover());
            for (com.maplecfo.planning.ExtraMoneyPlanner.Step s : p.steps()) out.println("  - " + s.target() + ": " + s.reason());
            out.println("Based on: " + String.join("; ", p.facts()));
            if (!noAi && !root.facade().getLlm().isMock()) {
                out.println();
                out.println(root.facade().explainExtraMoney(amount).answer());
            }
            return 0;
        }
    }
}
