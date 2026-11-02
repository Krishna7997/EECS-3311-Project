package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.persistence.TransactionRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public class SqliteTransactionRepository implements TransactionRepository {

    private static final String COLUMNS =
            "id, date, description, merchant, amount, account_id, category, category_source";
    private final Jdbc jdbc;

    public SqliteTransactionRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(Transaction t) {
        jdbc.update("INSERT OR REPLACE INTO transactions (" + COLUMNS + ", fingerprint) VALUES (?,?,?,?,?,?,?,?,?)",
                t.getId(), t.getDate().toString(), t.getDescription(), t.getMerchant(),
                t.getAmount().getAmount().toPlainString(), t.getAccountId(), t.getCategory().name(),
                t.getCategorySource().name(), t.fingerprint());
    }

    @Override
    public Optional<Transaction> findById(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM transactions WHERE id = ?", SqliteTransactionRepository::map, id)
                .stream().findFirst();
    }

    @Override
    public List<Transaction> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM transactions ORDER BY date", SqliteTransactionRepository::map);
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM transactions WHERE id = ?", id);
    }

    @Override
    public List<Transaction> findByMonth(YearMonth month) {
        return findBetween(month.atDay(1), month.atEndOfMonth());
    }

    @Override
    public List<Transaction> findBetween(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT " + COLUMNS + " FROM transactions WHERE date BETWEEN ? AND ? ORDER BY date",
                SqliteTransactionRepository::map, from.toString(), to.toString());
    }

    @Override
    public boolean existsByFingerprint(String fingerprint) {
        return countByFingerprint(fingerprint) > 0;
    }

    @Override
    public int countByFingerprint(String fingerprint) {
        return jdbc.query("SELECT COUNT(*) AS n FROM transactions WHERE fingerprint = ?",
                rs -> rs.getInt("n"), fingerprint).get(0);
    }

    private static Transaction map(ResultSet rs) throws SQLException {
        Transaction t = new Transaction(rs.getString("id"), LocalDate.parse(rs.getString("date")),
                rs.getString("description"), rs.getString("merchant"), Money.of(rs.getString("amount")),
                rs.getString("account_id"));
        t.setCategory(Category.valueOf(rs.getString("category")), CategorySource.valueOf(rs.getString("category_source")));
        return t;
    }
}
