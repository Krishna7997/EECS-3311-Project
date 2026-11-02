package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.ContributionRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class SqliteContributionRepository implements ContributionRepository {

    private final Jdbc jdbc;

    public SqliteContributionRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void add(ContributionRecord r) {
        jdbc.update("INSERT INTO contributions (account_type, date, amount, kind) VALUES (?,?,?,?)",
                r.accountType().name(), r.date().toString(), r.amount().getAmount().toPlainString(), r.kind().name());
    }

    @Override
    public void remove(ContributionRecord r) {
        jdbc.update("""
                DELETE FROM contributions WHERE id = (SELECT id FROM contributions
                WHERE account_type = ? AND date = ? AND amount = ? AND kind = ? LIMIT 1)""",
                r.accountType().name(), r.date().toString(), r.amount().getAmount().toPlainString(), r.kind().name());
    }

    @Override
    public List<ContributionRecord> findByType(AccountType type) {
        return jdbc.query("SELECT * FROM contributions WHERE account_type = ? ORDER BY date",
                SqliteContributionRepository::map, type.name());
    }

    private static ContributionRecord map(ResultSet rs) throws SQLException {
        return new ContributionRecord(AccountType.valueOf(rs.getString("account_type")),
                LocalDate.parse(rs.getString("date")), Money.of(rs.getString("amount")),
                ContributionKind.valueOf(rs.getString("kind")));
    }
}
