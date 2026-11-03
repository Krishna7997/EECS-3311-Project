package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.Money;
import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.persistence.GoalRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class SqliteGoalRepository implements GoalRepository {

    private final Jdbc jdbc;

    public SqliteGoalRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(SavingsGoal g) {
        jdbc.update("INSERT OR REPLACE INTO goals (id, name, target, saved, deadline, linked_account_id) VALUES (?,?,?,?,?,?)",
                g.getId(), g.getName(), g.getTarget().getAmount().toPlainString(), g.getSaved().getAmount().toPlainString(),
                g.getDeadline() == null ? null : g.getDeadline().toString(), g.getLinkedAccountId());
    }

    @Override
    public Optional<SavingsGoal> findById(String id) {
        return jdbc.query("SELECT * FROM goals WHERE id = ?", SqliteGoalRepository::map, id).stream().findFirst();
    }

    @Override
    public List<SavingsGoal> findAll() {
        return jdbc.query("SELECT * FROM goals ORDER BY deadline", SqliteGoalRepository::map);
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM goals WHERE id = ?", id);
    }

    private static SavingsGoal map(ResultSet rs) throws SQLException {
        String d = rs.getString("deadline");
        return new SavingsGoal(rs.getString("id"), rs.getString("name"), Money.of(rs.getString("target")),
                Money.of(rs.getString("saved")), d == null ? null : LocalDate.parse(d), rs.getString("linked_account_id"));
    }
}
