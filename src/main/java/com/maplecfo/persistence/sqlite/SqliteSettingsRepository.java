package com.maplecfo.persistence.sqlite;

import com.maplecfo.persistence.SettingsRepository;

import java.util.List;
import java.util.Optional;

public class SqliteSettingsRepository implements SettingsRepository {

    private final Jdbc jdbc;

    public SqliteSettingsRepository(DatabaseManager db) {
        this.jdbc = new Jdbc(db);
    }

    @Override
    public Optional<String> get(String key) {
        List<String> v = jdbc.query("SELECT value FROM settings WHERE key = ?", rs -> rs.getString("value"), key);
        return v.isEmpty() ? Optional.empty() : Optional.ofNullable(v.get(0));
    }

    @Override
    public void put(String key, String value) {
        jdbc.update("INSERT OR REPLACE INTO settings (key, value) VALUES (?,?)", key, value);
    }
}
