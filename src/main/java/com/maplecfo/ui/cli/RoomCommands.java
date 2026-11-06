package com.maplecfo.ui.cli;

import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.planning.ContributionCheck;
import com.maplecfo.planning.ContributionRoom;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

/** CLI commands for F06 TFSA / RRSP / FHSA room and the user profile it needs. */
@Command(name = "room", description = "TFSA / RRSP / FHSA contribution room (F06).",
        subcommands = {RoomCommands.CheckCmd.class, RoomCommands.LogCmd.class})
final class RoomCommands implements Callable<Integer> {

    @CommandLine.ParentCommand MapleCfoCli root;
    @CommandLine.Spec CommandLine.Model.CommandSpec spec;
    @Parameters(arity = "0..1", description = "TFSA, RRSP or FHSA (default: all three)") AccountType type;
    @Option(names = {"-y", "--year"}, description = "Year (default: this year)") Integer year;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        int y = year != null ? year : root.facade().today().getYear();
        List<AccountType> types = type != null ? List.of(type) : List.of(AccountType.TFSA, AccountType.RRSP, AccountType.FHSA);
        for (AccountType t : types) {
            List<String> missing = root.facade().getProfile().missingFieldsFor(t);
            if (!missing.isEmpty()) {
                out.println(t + ": unknown. Set your profile first: " + String.join(", ", missing)
                        + "  (see: maplecfo profile set --help)");
                continue;
            }
            ContributionRoom r = root.facade().getContributionRoom(t, y);
            out.println(t + " " + y + ": room " + r.limit() + ", used " + r.used() + ", available " + r.available()
                    + (r.isOverContributed() ? "   OVER-CONTRIBUTED" : ""));
        }
        return 0;
    }

    @Command(name = "check", description = "Check a contribution before you make it.")
    static class CheckCmd implements Callable<Integer> {
        @CommandLine.ParentCommand RoomCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(index = "0") AccountType type;
        @Parameters(index = "1", description = "Amount you plan to contribute") Money amount;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            ContributionCheck c = parent.root.facade().checkContribution(type, amount);
            switch (c.status()) {
                case OK -> out.println("OK: " + amount + " fits. Room left afterwards: " + c.roomAfter() + ".");
                case ALLOWANCE -> out.println("OK, within the RRSP allowance: " + amount + " is " + c.excess()
                        + " over your limit, but CRA allows up to $2,000 over without tax. You can't deduct the extra.");
                case OVER -> out.println("OVER: " + amount + " is " + c.excess() + " more than your " + type
                        + " room (" + c.room().available() + "). CRA charges 1% per month on the excess.");
                case UNKNOWN -> out.println("UNKNOWN: missing " + String.join(", ", c.missingFields()) + ".");
            }
            parent.root.printNewAlerts(out);
            return 0;
        }
    }

    @Command(name = "log", description = "Record a contribution or withdrawal you made.")
    static class LogCmd implements Callable<Integer> {
        @CommandLine.ParentCommand RoomCommands parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @Parameters(index = "0") AccountType type;
        @Parameters(index = "1") Money amount;
        @Option(names = "--withdrawal", description = "This was a withdrawal") boolean withdrawal;
        @Option(names = "--date", description = "Date (default: today), e.g. 2026-01-15") LocalDate date;

        @Override
        public Integer call() {
            LocalDate d = date != null ? date : parent.root.facade().today();
            ContributionKind kind = withdrawal ? ContributionKind.WITHDRAWAL : ContributionKind.CONTRIBUTION;
            parent.root.facade().recordContribution(new ContributionRecord(type, d, amount, kind));
            spec.commandLine().getOut().println("Recorded " + kind.name().toLowerCase() + " of " + amount + " to " + type + " on " + d + ".");
            return 0;
        }
    }

    @Command(name = "profile", description = "Show or set the profile used for contribution room.",
            subcommands = {ProfileCmd.ShowCmd.class, ProfileCmd.SetCmd.class})
    static class ProfileCmd {
        @CommandLine.ParentCommand MapleCfoCli root;

        @Command(name = "show")
        static class ShowCmd implements Callable<Integer> {
            @CommandLine.ParentCommand ProfileCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;

            @Override
            public Integer call() {
                UserProfile p = parent.root.facade().getProfile();
                PrintWriter out = spec.commandLine().getOut();
                out.println("Birth year:           " + orDash(p.getBirthYear()));
                out.println("Province:             " + orDash(p.getProvince()));
                out.println("Resident since:       " + orDash(p.getResidentSinceYear()));
                out.println("RRSP deduction limit: " + orDash(p.getRrspDeductionLimit()));
                out.println("FHSA opened:          " + orDash(p.getFhsaOpenedYear()));
                return 0;
            }

            private static String orDash(Object o) {
                return o == null ? "-" : o.toString();
            }
        }

        @Command(name = "set", description = "Change only the fields you give.")
        static class SetCmd implements Callable<Integer> {
            @CommandLine.ParentCommand ProfileCmd parent;
            @CommandLine.Spec CommandLine.Model.CommandSpec spec;
            @Option(names = "--birth-year") Integer birthYear;
            @Option(names = "--province") String province;
            @Option(names = "--resident-since", description = "Year you became a resident of Canada") Integer residentSince;
            @Option(names = "--rrsp-limit", description = "RRSP deduction limit from your CRA Notice of Assessment") Money rrspLimit;
            @Option(names = "--fhsa-opened", description = "Year you opened your FHSA") Integer fhsaOpened;

            @Override
            public Integer call() {
                UserProfile p = parent.root.facade().getProfile();
                if (birthYear != null) p.setBirthYear(birthYear);
                if (province != null) p.setProvince(province);
                if (residentSince != null) p.setResidentSinceYear(residentSince);
                if (rrspLimit != null) p.setRrspDeductionLimit(rrspLimit);
                if (fhsaOpened != null) p.setFhsaOpenedYear(fhsaOpened);
                parent.root.facade().saveProfile(p);
                spec.commandLine().getOut().println("Profile saved.");
                return 0;
            }
        }
    }
}
