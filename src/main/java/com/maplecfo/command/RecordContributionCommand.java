package com.maplecfo.command;

import com.maplecfo.domain.Account;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.AccountService;
import com.maplecfo.planning.NetWorthService;
import com.maplecfo.planning.RegisteredAccountService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Logs a TFSA/RRSP/FHSA contribution (F06, proposed automatically by F14); undo removes it.
 * When the user has exactly one account of that type, its balance is moved by the same amount, so the
 * money that left chequing shows up in the TFSA and net worth stays the same.
 */
public class RecordContributionCommand implements UndoableCommand {

    private final RegisteredAccountService registered;
    private final ContributionRecord record;
    private final AccountService accounts;
    private final NetWorthService netWorth;
    private final String targetAccountId;
    private final String targetAccountName;
    private Money previousBalance;

    public RecordContributionCommand(RegisteredAccountService registered, ContributionRecord record) {
        this(registered, record, null, null);
    }

    public RecordContributionCommand(RegisteredAccountService registered, ContributionRecord record,
                                     AccountService accounts, NetWorthService netWorth) {
        this.registered = Objects.requireNonNull(registered);
        this.record = Objects.requireNonNull(record);
        this.accounts = accounts;
        this.netWorth = netWorth;
        Optional<Account> target = accounts == null ? Optional.empty() : onlyAccountOfType(accounts, record);
        this.targetAccountId = target.map(Account::getId).orElse(null);
        this.targetAccountName = target.map(Account::getName).orElse(null);
    }

    private static Optional<Account> onlyAccountOfType(AccountService accounts, ContributionRecord r) {
        List<Account> same = accounts.getAccounts().stream().filter(a -> a.getType() == r.accountType()).toList();
        return same.size() == 1 ? Optional.of(same.get(0)) : Optional.empty();
    }

    public ContributionRecord record() {
        return record;
    }

    /** The account whose balance is changed, if any. */
    public Optional<String> targetAccountName() {
        return Optional.ofNullable(targetAccountName);
    }

    @Override
    public void execute() {
        registered.recordContribution(record);
        if (targetAccountId != null) {
            accounts.find(targetAccountId).ifPresent(a -> {
                previousBalance = a.getBalance();
                Money moved = record.kind() == ContributionKind.CONTRIBUTION ? record.amount() : record.amount().negate();
                accounts.updateBalance(a.getId(), a.getBalance().plus(moved).max(Money.zero()));
                if (netWorth != null) netWorth.recordSnapshot();
            });
        }
    }

    @Override
    public void undo() {
        registered.removeContribution(record);
        if (targetAccountId != null && previousBalance != null) {
            accounts.updateBalance(targetAccountId, previousBalance);
            if (netWorth != null) netWorth.recordSnapshot();
        }
    }

    @Override
    public String describe() {
        String base = "Record " + record.amount() + " " + record.accountType() + " "
                + record.kind().name().toLowerCase() + " on " + record.date();
        if (targetAccountName == null) return base;
        return base + (record.kind() == ContributionKind.CONTRIBUTION ? " and add it to " : " and take it from ")
                + targetAccountName;
    }
}
