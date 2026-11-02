package com.maplecfo.persistence.sqlite;

import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.persistence.MappingProfileRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class SqliteMappingProfileRepository implements MappingProfileRepository {

    private final Jdbc jdbc;

    public SqliteMappingProfileRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public void save(ColumnMapping m) {
        jdbc.update("""
                INSERT OR REPLACE INTO mapping_profiles (name, has_header, date_column, date_format,
                description_column, amount_column, debit_column, credit_column, negate_amounts, balance_column)
                VALUES (?,?,?,?,?,?,?,?,?,?)""",
                m.profileName(), m.hasHeader() ? 1 : 0, m.dateColumn(), m.dateFormat(), m.descriptionColumn(),
                m.amountColumn(), m.debitColumn(), m.creditColumn(), m.negateAmounts() ? 1 : 0, m.balanceColumn());
    }

    @Override
    public Optional<ColumnMapping> findByName(String name) {
        return jdbc.query("SELECT * FROM mapping_profiles WHERE name = ? COLLATE NOCASE",
                SqliteMappingProfileRepository::map, name).stream().findFirst();
    }

    @Override
    public List<ColumnMapping> findAll() {
        return jdbc.query("SELECT * FROM mapping_profiles ORDER BY name", SqliteMappingProfileRepository::map);
    }

    private static ColumnMapping map(ResultSet rs) throws SQLException {
        return new ColumnMapping(rs.getString("name"), rs.getInt("has_header") == 1, rs.getString("date_column"),
                rs.getString("date_format"), rs.getString("description_column"), rs.getString("amount_column"),
                rs.getString("debit_column"), rs.getString("credit_column"), rs.getInt("negate_amounts") == 1,
                rs.getString("balance_column"));
    }
}
