package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.AccountRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SqliteAccountRepository implements AccountRepository {

    private final Jdbc jdbc;

    public SqliteAccountRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(Account a) {
        jdbc.update("INSERT OR REPLACE INTO accounts (id, name, type, institution, balance) VALUES (?,?,?,?,?)",
                a.getId(), a.getName(), a.getType().name(), a.getInstitution(), a.getBalance().getAmount().toPlainString());
    }

    @Override
    public Optional<Account> findById(String id) {
        return jdbc.query("SELECT * FROM accounts WHERE id = ?", SqliteAccountRepository::map, id).stream().findFirst();
    }

    @Override
    public List<Account> findAll() {
        return jdbc.query("SELECT * FROM accounts ORDER BY name", SqliteAccountRepository::map);
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM accounts WHERE id = ?", id);
    }

    private static Account map(ResultSet rs) throws SQLException {
        return new Account(rs.getString("id"), rs.getString("name"), AccountType.valueOf(rs.getString("type")),
                rs.getString("institution"), Money.of(rs.getString("balance")));
    }
}
