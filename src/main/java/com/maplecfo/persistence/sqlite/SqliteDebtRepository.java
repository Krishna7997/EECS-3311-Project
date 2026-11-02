package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.DebtRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SqliteDebtRepository implements DebtRepository {

    private final Jdbc jdbc;

    public SqliteDebtRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(Debt d) {
        jdbc.update("INSERT OR REPLACE INTO debts (id, name, balance, apr, min_payment, account_id) VALUES (?,?,?,?,?,?)",
                d.getId(), d.getName(), d.getBalance().getAmount().toPlainString(), d.getApr(),
                d.getMinPayment().getAmount().toPlainString(), d.getAccountId());
    }

    @Override
    public Optional<Debt> findById(String id) {
        return jdbc.query("SELECT * FROM debts WHERE id = ?", SqliteDebtRepository::map, id).stream().findFirst();
    }

    @Override
    public List<Debt> findAll() {
        return jdbc.query("SELECT * FROM debts ORDER BY name", SqliteDebtRepository::map);
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM debts WHERE id = ?", id);
    }

    private static Debt map(ResultSet rs) throws SQLException {
        return new Debt(rs.getString("id"), rs.getString("name"), Money.of(rs.getString("balance")),
                rs.getDouble("apr"), Money.of(rs.getString("min_payment")), rs.getString("account_id"));
    }
}
