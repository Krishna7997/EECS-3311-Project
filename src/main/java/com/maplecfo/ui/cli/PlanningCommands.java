package com.maplecfo.ui.cli;

import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.planning.FireInputs;
import com.maplecfo.planning.FireProjection;
import com.maplecfo.planning.PayoffMethod;
import com.maplecfo.planning.PayoffPlan;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** CLI commands for F05 (recurring charges), F07 (FIRE) and F08 (debt payoff). */
final class PlanningCommands {

    private PlanningCommands() {
    }

    @Command(name = "fire", description = "Project when you could reach financial independence (F07). "
            + "e.g. fire --mine, or fire --save 15000 --spend 40000 --target 1000000 --retire-at 50")
    static class FireCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Option(names = "--mine", description = "Start from your own accounts and recent cash flow") boolean mine;
        @Option(names = "--invested", description = "Invested today") Money invested;
        @Option(names = "--save", description = "Saved per year") Money save;
        @Option(names = "--spend", description = "Spending per year") Money spend;
        @Option(names = "--return", description = "Real return, e.g. 0.05 (default)") Double realReturn;
        @Option(names = "--withdrawal", description = "Withdrawal rate (default 0.04)") Double withdrawal;
        @Option(names = "--age", description = "Your age (default: from your profile, else 22)") Integer age;
        @Option(names = "--target", description = "Your own target instead of spending / withdrawal rate") Money target;
        @Option(names = "--retire-at", description = "Age you'd like to retire at: shows the saving needed") Integer retireAt;

        @Override
        public Integer call() {
            FireInputs base = mine ? root.facade().suggestFireInputs() : null;
            if (base == null && (save == null || spend == null)) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Give --save and --spend, or use --mine");
            }
            Integer born = root.facade().getProfile().getBirthYear();
            int a = age != null ? age : base != null ? base.currentAge()
                    : born != null ? root.facade().today().getYear() - born : 22;
            FireInputs in = new FireInputs(
                    invested != null ? invested : base != null ? base.invested() : Money.zero(),
                    save != null ? save : base.yearlySaving(), spend != null ? spend : base.annualSpending(),
                    realReturn != null ? realReturn : 0.05, withdrawal != null ? withdrawal : 0.04, a, target, retireAt);
            FireProjection p = root.facade().projectFire(in);
            PrintWriter out = spec.commandLine().getOut();
            if (mine) {
                out.println("Your numbers: invested " + in.invested() + ", saving " + in.yearlySaving() + " a year, spending "
                        + in.annualSpending() + " a year, age " + in.currentAge() + ".");
            }
            out.println("FIRE target: " + p.targetAmount() + (in.hasCustomTarget() ? " (your own target)"
                    : " (" + in.annualSpending() + " / " + com.maplecfo.planning.FireCalculator.percent(in.withdrawalRate()) + ")")
                    + "; it lets you spend " + p.safeYearlySpending() + " a year.");
            if (in.hasCustomTarget() && p.safeYearlySpending().isLessThan(in.annualSpending())) {
                out.println("Note: that is less than the " + in.annualSpending() + " a year you plan to spend.");
            }
            if (p.reachable()) {
                out.println("You reach it in " + p.yearsToFire() + " years, at age " + p.fireAge() + ".");
            } else {
                out.println("Not reachable within 70 years with these inputs. Try saving more or spending less.");
            }
            if (p.savingNeeded() != null) {
                out.println("To retire at " + in.targetAge() + ": save about " + p.savingNeeded() + " a year (you save "
                        + in.yearlySaving() + ").");
            }
            out.println("Coast FIRE number (age " + p.coastAge() + "): " + p.coastNumber()
                    + (p.coastReached() ? " - already reached" : ""));
            List<List<String>> rows = new ArrayList<>();
            for (FireProjection.Scenario s : p.scenarios()) {
                rows.add(List.of(s.label(), s.yearlySaving().toString(), s.annualSpending().toString(),
                        s.targetAmount().toString(), s.reachable() ? s.yearsToFire() + " years" : "not reachable"));
            }
            out.print(root.renderer().table(List.of("Scenario", ">Save/yr", ">Spend/yr", ">Target", ">Time"), rows));
            return 0;
        }
    }

    @Command(name = "debt", description = "Track debts and plan the payoff (F08).",
            subcommands = {DebtCmd.AddCmd.class, DebtCmd.ListCmd.class, DebtCmd.RemoveCmd.class, DebtCmd.PlanCmd.class})
    static class DebtCmd {
        @CommandLine.ParentCommand MapleCfoCli root;

        @Command(name = "add", description = "Add a debt, e.g. debt add \"RBC Visa\" --balance 2400 --apr 20.99 --min 60")
        static class AddCmd implements Callable<Integer> {
            @CommandLine.ParentCommand DebtCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Parameters(description = "Name") String name;
            @Option(names = "--balance", required = true) Money balance;
            @Option(names = "--apr", required = true, description = "Interest rate in %, e.g. 20.99") double apr;
            @Option(names = "--min", required = true, description = "Minimum monthly payment") Money min;

            @Override
            public Integer call() {
                parent.root.facade().addDebt(name, balance, apr, min);
                spec.commandLine().getOut().println("Added " + name + ".");
                return 0;
            }
        }

        @Command(name = "list")
        static class ListCmd implements Callable<Integer> {
            @CommandLine.ParentCommand DebtCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;

            @Override
            public Integer call() {
                CliRenderer r = parent.root.renderer();
                List<List<String>> rows = new ArrayList<>();
                for (Debt d : parent.root.facade().getDebts()) {
                    rows.add(List.of(r.shortId(d.getId()), d.getName(), d.getBalance().toString(), d.getApr() + "%",
                            d.getMinPayment().toString()));
                }
                spec.commandLine().getOut().print(r.table(List.of("Id", "Name", ">Balance", ">APR", ">Min/month"), rows));
                return 0;
            }
        }

        @Command(name = "remove")
        static class RemoveCmd implements Callable<Integer> {
            @CommandLine.ParentCommand DebtCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Parameters(description = "Debt id (first characters are enough)") String id;

            @Override
            public Integer call() {
                Debt d = parent.root.facade().getDebts().stream().filter(x -> x.getId().startsWith(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No debt with id " + id));
                parent.root.facade().removeDebt(d.getId());
                spec.commandLine().getOut().println("Removed " + d.getName() + ".");
                return 0;
            }
        }

        @Command(name = "plan", description = "Month-by-month payoff plan; --compare shows both methods.")
        static class PlanCmd implements Callable<Integer> {
            @CommandLine.ParentCommand DebtCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Option(names = "--method", defaultValue = "AVALANCHE", description = "${COMPLETION-CANDIDATES}") PayoffMethod method;
            @Option(names = "--extra", defaultValue = "0", description = "Extra paid each month") Money extra;
            @Option(names = "--compare", description = "Compare avalanche and snowball") boolean compare;

            @Override
            public Integer call() {
                PrintWriter out = spec.commandLine().getOut();
                List<PayoffMethod> methods = compare ? List.of(PayoffMethod.AVALANCHE, PayoffMethod.SNOWBALL) : List.of(method);
                List<List<String>> rows = new ArrayList<>();
                for (PayoffMethod m : methods) {
                    PayoffPlan p = parent.root.facade().planDebtPayoff(m, extra);
                    if (p.isDebtFreeAlready()) {
                        out.println(p.warnings().get(0));
                        return 0;
                    }
                    rows.add(List.of(p.strategyName(), p.completes() ? p.monthsToDebtFree() + " months" : "over 50 years",
                            p.completes() ? p.debtFreeMonth().toString() : "-", p.totalInterest().toString()));
                    if (!compare) {
                        out.println("Payoff order and dates:");
                        p.payoffs().forEach(po -> out.println("  " + po.debtName() + ": paid off " + po.payoffMonth()));
                    }
                    p.warnings().forEach(w -> out.println("Warning: " + w));
                }
                out.print(parent.root.renderer().table(List.of("Method", ">Debt-free in", ">Month", ">Total interest"), rows));
                return 0;
            }
        }
    }

    @Command(name = "recurring", description = "Subscriptions and other recurring charges (F05).")
    static class RecurringCmd implements Callable<Integer> {
        @CommandLine.ParentCommand MapleCfoCli root;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            List<RecurringCharge> charges = root.facade().getRecurringCharges();
            if (charges.isEmpty()) {
                out.println("No recurring charges found yet. Import a few months of transactions first.");
                return 0;
            }
            List<List<String>> rows = new ArrayList<>();
            Money yearly = Money.zero();
            for (RecurringCharge c : charges) {
                String note = (c.priceIncreased() ? "price went up " : "") + (c.variableAmount() ? "variable amount" : "");
                rows.add(List.of(root.renderer().truncate(c.merchant(), 30), c.frequency().name().toLowerCase(),
                        c.averageAmount().toString(), c.yearlyCost().toString(), c.nextExpectedDate().toString(), note.trim()));
                yearly = yearly.plus(c.yearlyCost());
            }
            out.print(root.renderer().table(List.of("Merchant", "Every", ">Average", ">Per year", "Next", "Note"), rows));
            out.println("Total per year: " + yearly);
            if (root.facade().isRecurringLowConfidence()) {
                out.println("Note: less than 3 months of data, so these results are less certain.");
            }
            return 0;
        }
    }
}
