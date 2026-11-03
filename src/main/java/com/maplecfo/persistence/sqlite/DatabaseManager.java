package com.maplecfo.persistence.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens the local SQLite database and creates the tables on first use.
 * All data stays on the user's computer (Stage 1 decision D5).
 */
public class DatabaseManager {

    private final String url;

    public DatabaseManager(String url) {
        this.url = url;
    }

    /** A database file, creating its folder if needed, e.g. ~/.maplecfo/maplecfo.db. */
    public static DatabaseManager forFile(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new PersistenceException("Cannot create folder for " + file, e);
        }
        DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + file.toAbsolutePath());
        db.migrate();
        return db;
    }

    public Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
        }
        return c;
    }

    /** Creates any missing tables. Safe to run on every start. */
    public void migrate() {
        String[] ddl = {
            """
            CREATE TABLE IF NOT EXISTS accounts (
              id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL,
              institution TEXT, balance TEXT NOT NULL)""",
            """
            CREATE TABLE IF NOT EXISTS transactions (
              id TEXT PRIMARY KEY, date TEXT NOT NULL, description TEXT NOT NULL, merchant TEXT,
              amount TEXT NOT NULL, account_id TEXT NOT NULL, category TEXT NOT NULL,
              category_source TEXT NOT NULL, fingerprint TEXT NOT NULL)""",
            "CREATE INDEX IF NOT EXISTS idx_tx_fingerprint ON transactions(fingerprint)",
            "CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions(date)",
            """
            CREATE TABLE IF NOT EXISTS budgets (
              category TEXT NOT NULL, month TEXT NOT NULL, amount_limit TEXT NOT NULL,
              PRIMARY KEY (category, month))""",
            """
            CREATE TABLE IF NOT EXISTS debts (
              id TEXT PRIMARY KEY, name TEXT NOT NULL, balance TEXT NOT NULL, apr REAL NOT NULL,
              min_payment TEXT NOT NULL, account_id TEXT)""",
            """
            CREATE TABLE IF NOT EXISTS contributions (
              id INTEGER PRIMARY KEY AUTOINCREMENT, account_type TEXT NOT NULL, date TEXT NOT NULL,
              amount TEXT NOT NULL, kind TEXT NOT NULL)""",
            """
            CREATE TABLE IF NOT EXISTS profile (
              id INTEGER PRIMARY KEY CHECK (id = 1), birth_year INTEGER, province TEXT,
              resident_since_year INTEGER, annual_income TEXT, rrsp_deduction_limit TEXT,
              fhsa_opened_year INTEGER)""",
            """
            CREATE TABLE IF NOT EXISTS mapping_profiles (
              name TEXT PRIMARY KEY, has_header INTEGER NOT NULL, date_column TEXT NOT NULL,
              date_format TEXT NOT NULL, description_column TEXT NOT NULL, amount_column TEXT,
              debit_column TEXT, credit_column TEXT, negate_amounts INTEGER NOT NULL, balance_column TEXT)""",
            """
            CREATE TABLE IF NOT EXISTS category_rules (
              keyword TEXT PRIMARY KEY, category TEXT NOT NULL)""",
            """
            CREATE TABLE IF NOT EXISTS goals (
              id TEXT PRIMARY KEY, name TEXT NOT NULL, target TEXT NOT NULL, saved TEXT NOT NULL,
              deadline TEXT, linked_account_id TEXT)""",
            """
            CREATE TABLE IF NOT EXISTS net_worth_history (
              date TEXT PRIMARY KEY, total TEXT NOT NULL)"""
        };
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            for (String sql : ddl) {
                s.execute(sql);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Could not create the database tables", e);
        }
    }

    public String getUrl() {
        return url;
    }
}
