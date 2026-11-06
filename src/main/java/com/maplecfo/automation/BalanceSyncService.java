package com.maplecfo.automation;

import com.maplecfo.domain.Account;
import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.events.FinanceEventListener;
import com.maplecfo.importer.ImportResult;
import com.maplecfo.persistence.DebtRepository;
import com.maplecfo.planning.AccountService;
import com.maplecfo.planning.NetWorthService;

/**
 * F13 balance sync (Observer): after an import that includes a running balance, the matching account
 * (and any debt linked to it) is updated, so net worth stays current without typing balances.
 */
public class BalanceSyncService implements FinanceEventListener {

    private final AccountService accounts;
    private final DebtRepository debts;
    private final NetWorthService netWorth;

    public BalanceSyncService(AccountService accounts, DebtRepository debts, NetWorthService netWorth) {
        this.accounts = accounts;
        this.debts = debts;
        this.netWorth = netWorth;
    }

    public void register(EventBus bus) {
        bus.subscribe(EventType.TRANSACTIONS_IMPORTED, this);
    }

    @Override
    public void onEvent(FinanceEvent e) {
        if (!(e.payload() instanceof ImportResult r) || r.latestBalanceIfKnown().isEmpty()) {
            return;
        }
        Money balance = r.latestBalanceIfKnown().get();
        boolean changed = false;
        Account a = accounts.find(r.accountId()).orElse(null);
        if (a != null) {
            // a card statement shows what is owed as a negative running balance; accounts store debts as positive
            Money value = a.isLiability() ? balance.abs() : balance;
            if (!value.isNegative() && !value.equals(a.getBalance())) {
                accounts.updateBalance(a.getId(), value);
                changed = true;
            }
        }
        for (Debt d : debts.findAll()) {
            if (r.accountId().equals(d.getAccountId())) {
                d.updateBalance(balance.abs());
                debts.save(d);
                changed = true;
            }
        }
        if (changed) {
            netWorth.recordSnapshot();
        }
    }
}
