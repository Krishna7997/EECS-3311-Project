package com.maplecfo.automation;

import com.maplecfo.agent.PendingActionStore;
import com.maplecfo.command.RecordContributionCommand;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.events.FinanceEventListener;
import com.maplecfo.importer.ImportResult;
import com.maplecfo.planning.RegisteredAccountService;
import com.maplecfo.services.TransactionService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * F14 (Observer): after each import, looks for transfers into a TFSA, RRSP or FHSA and proposes
 * logging them. Nothing is recorded until the user confirms (Command in the PendingActionStore).
 * A transaction is proposed at most once, so a dismissed proposal does not come back.
 */
public class ContributionDetector implements FinanceEventListener {

    private static final List<AccountType> TYPES = List.of(AccountType.TFSA, AccountType.RRSP, AccountType.FHSA);

    private final TransactionService txService;
    private final RegisteredAccountService registered;
    private final PendingActionStore pending;
    private final EventBus eventBus;
    private final Set<String> alreadyProposed = new HashSet<>();
    private com.maplecfo.planning.AccountService accounts;
    private com.maplecfo.planning.NetWorthService netWorth;

    public ContributionDetector(TransactionService txService, RegisteredAccountService registered,
                                PendingActionStore pending, EventBus eventBus) {
        this.txService = txService;
        this.registered = registered;
        this.pending = pending;
        this.eventBus = eventBus;
    }

    /** With accounts, a confirmed contribution also moves the money into the TFSA/RRSP/FHSA account balance. */
    public ContributionDetector withAccounts(com.maplecfo.planning.AccountService accounts,
                                             com.maplecfo.planning.NetWorthService netWorth) {
        this.accounts = accounts;
        this.netWorth = netWorth;
        return this;
    }

    public void register() {
        eventBus.subscribe(EventType.TRANSACTIONS_IMPORTED, this);
    }

    @Override
    public void onEvent(FinanceEvent e) {
        if (e.payload() instanceof ImportResult r) {
            scan(r.importedIds());
        }
    }

    /** Proposes a record for each clear contribution among these transactions; returns the action ids. */
    public List<String> scan(List<String> transactionIds) {
        List<String> ids = new ArrayList<>();
        for (String txId : transactionIds) {
            Optional<Transaction> t = txService.findById(txId);
            if (t.isEmpty() || !alreadyProposed.add(txId)) continue;
            Optional<ContributionRecord> rec = detect(t.get());
            if (rec.isEmpty() || alreadyLogged(rec.get())) continue;
            RecordContributionCommand cmd = new RecordContributionCommand(registered, rec.get(), accounts, netWorth);
            String actionId = pending.add(cmd);
            ids.add(actionId);
            eventBus.publish(FinanceEvent.of(EventType.CONTRIBUTION_DETECTED, "Detected a " + rec.get().amount() + " "
                    + rec.get().accountType() + " " + rec.get().kind().name().toLowerCase() + " on "
                    + rec.get().date() + " (" + t.get().getDescription() + "). Confirm with: confirm " + actionId
                    + (cmd.targetAccountName().isEmpty() && accounts != null
                    ? ". Tip: add your " + rec.get().accountType() + " account so its balance is updated too." : "")));
        }
        return ids;
    }

    /** A transaction is a contribution only when its description names exactly one registered account. */
    static Optional<ContributionRecord> detect(Transaction t) {
        String d = " " + t.getDescription().toUpperCase().replaceAll("[^A-Z]", " ") + " ";
        List<AccountType> named = TYPES.stream().filter(type -> d.contains(" " + type.name() + " ")).toList();
        if (named.size() != 1) {
            return Optional.empty(); // none, or ambiguous like "TFSA TO RRSP"
        }
        Money amount = t.getAmount().abs();
        if (amount.isZero()) return Optional.empty();
        ContributionKind kind;
        if (t.isExpense()) {
            kind = ContributionKind.CONTRIBUTION;
        } else if (d.contains(" WITHDRAWAL ") || d.contains(" WITHDRAW ")) {
            kind = ContributionKind.WITHDRAWAL;
        } else {
            return Optional.empty(); // money in that does not say withdrawal: unclear
        }
        return Optional.of(new ContributionRecord(named.get(0), t.getDate(), amount, kind));
    }

    private boolean alreadyLogged(ContributionRecord r) {
        return registered.getContributions(r.accountType()).stream().anyMatch(x -> x.equals(r));
    }
}
