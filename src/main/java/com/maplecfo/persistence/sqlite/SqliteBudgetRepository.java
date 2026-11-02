package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.Budget;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.BudgetRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public class SqliteBudgetRepository implements BudgetRepository {

    private final Jdbc jdbc;

    public SqliteBudgetRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(Budget b) {
        jdbc.update("INSERT OR REPLACE INTO budgets (category, month, amount_limit) VALUES (?,?,?)",
                b.category().name(), b.month().toString(), b.limit().getAmount().toPlainString());
    }

    @Override
    public Optional<Budget> find(Category c, YearMonth m) {
        return jdbc.query("SELECT * FROM budgets WHERE category = ? AND month = ?", SqliteBudgetRepository::map,
                c.name(), m.toString()).stream().findFirst();
    }

    @Override
    public List<Budget> findByMonth(YearMonth m) {
        return jdbc.query("SELECT * FROM budgets WHERE month = ? ORDER BY category", SqliteBudgetRepository::map,
                m.toString());
    }

    @Override
    public void delete(Category c, YearMonth m) {
        jdbc.update("DELETE FROM budgets WHERE category = ? AND month = ?", c.name(), m.toString());
    }

    private static Budget map(ResultSet rs) throws SQLException {
        return new Budget(Category.valueOf(rs.getString("category")), YearMonth.parse(rs.getString("month")),
                Money.of(rs.getString("amount_limit")));
    }
}
