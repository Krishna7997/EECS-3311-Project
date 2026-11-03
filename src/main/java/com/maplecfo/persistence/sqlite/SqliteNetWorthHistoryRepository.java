package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.Money;
import com.maplecfo.persistence.NetWorthHistoryRepository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class SqliteNetWorthHistoryRepository implements NetWorthHistoryRepository {

    private final Jdbc jdbc;

    public SqliteNetWorthHistoryRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(LocalDate date, Money total) {
        jdbc.update("INSERT OR REPLACE INTO net_worth_history (date, total) VALUES (?,?)",
                date.toString(), total.getAmount().toPlainString());
    }

    @Override
    public Map<LocalDate, Money> findAll() {
        Map<LocalDate, Money> out = new LinkedHashMap<>();
        jdbc.query("SELECT * FROM net_worth_history ORDER BY date", rs -> {
            out.put(LocalDate.parse(rs.getString("date")), Money.of(rs.getString("total")));
            return null;
        });
        return out;
    }
}
