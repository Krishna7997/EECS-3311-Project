package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.AbstractFinanceTool;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.AccountService;
import com.maplecfo.planning.NetWorthService;
import com.maplecfo.planning.NetWorthSnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Net worth, split into groups and accounts, plus the change since the first history point (F03). */
public class NetWorthTool extends AbstractFinanceTool {

    private final NetWorthService netWorth;
    private final AccountService accounts;

    public NetWorthTool(NetWorthService netWorth, AccountService accounts) {
        this.netWorth = netWorth;
        this.accounts = accounts;
    }

    @Override public String name() { return "net_worth"; }

    @Override public String description() {
        return "The user's net worth: total, assets, liabilities, each group (Cash, Registered, Investments, "
                + "Debts) and each account balance, plus the change since the first recorded snapshot.";
    }

    @Override public JsonNode schema() {
        return schemaOf();
    }

    @Override
    protected ObjectNode doExecute(JsonNode args) {
        NetWorthSnapshot s = netWorth.computeSnapshot();
        List<Account> list = accounts.getAccounts();
        ObjectNode out = obj();
        out.put("date", s.date().toString());
        out.put("net_worth", s.total().toString());
        out.put("assets", s.assets().toString());
        out.put("liabilities", s.liabilities().toString());
        ObjectNode groups = out.putObject("groups");
        s.groups().forEach((k, v) -> groups.put(k, v.toString()));
        ArrayNode accs = out.putArray("accounts");
        for (Account a : list) {
            ObjectNode o = accs.addObject();
            o.put("name", a.getName());
            o.put("type", a.getType().name());
            o.put("balance", a.getBalance().toString());
            o.put("is_debt", a.isLiability());
        }
        Map<LocalDate, Money> history = netWorth.getHistory();
        String change = "";
        if (history.size() > 1) {
            Map.Entry<LocalDate, Money> first = history.entrySet().iterator().next();
            Money diff = s.total().minus(first.getValue());
            out.put("first_snapshot_date", first.getKey().toString());
            out.put("change_since_first_snapshot", diff.toString());
            change = " Change since " + first.getKey() + ": " + diff + ".";
        }
        out.put("summary", list.isEmpty()
                ? "The user has not added any accounts yet, so net worth is unknown."
                : "Net worth is " + s.total() + " (assets " + s.assets() + ", debts " + s.liabilities() + ")."
                + change);
        return out;
    }
}
