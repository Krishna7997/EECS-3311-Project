package com.maplecfo.planning;

import com.maplecfo.domain.AccountGroup;
import com.maplecfo.domain.AssetComponent;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.NetWorthHistoryRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F03 net worth. Asks the Composite root for its value (it adds up every
 * group and account) and keeps one history point per day for the chart.
 */
public class NetWorthService {

    private final AccountService accountService;
    private final NetWorthHistoryRepository history;
    private final Clock clock;

    public NetWorthService(AccountService accountService, NetWorthHistoryRepository history, Clock clock) {
        this.accountService = accountService;
        this.history = history;
        this.clock = clock;
    }

    public NetWorthSnapshot computeSnapshot() {
        AccountGroup root = accountService.buildAccountTree();
        Map<String, Money> groups = new LinkedHashMap<>();
        Money assets = Money.zero();
        Money liabilities = Money.zero();
        for (AssetComponent g : root.getChildren()) {
            groups.put(g.getName(), g.getValue());
            if (g.getValue().isNegative()) {
                liabilities = liabilities.plus(g.getValue().abs());
            } else {
                assets = assets.plus(g.getValue());
            }
        }
        return new NetWorthSnapshot(LocalDate.now(clock), root.getValue(), assets, liabilities, groups);
    }

    /** Saves today's total (one point per day; a later save the same day replaces it). */
    public NetWorthSnapshot recordSnapshot() {
        NetWorthSnapshot s = computeSnapshot();
        history.save(s.date(), s.total());
        return s;
    }

    public Map<LocalDate, Money> getHistory() {
        return history.findAll();
    }

    public List<AssetComponent> groups() {
        return accountService.buildAccountTree().getChildren();
    }
}
