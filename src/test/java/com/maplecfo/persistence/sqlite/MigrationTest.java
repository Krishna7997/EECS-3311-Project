package com.maplecfo.persistence.sqlite;

import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** A database made by an older MapleCFO keeps its data and gets the new columns. */
class MigrationTest {

    @TempDir
    Path dir;

    @Test
    void oldProfileTableGetsThePensionAdjustmentColumn() throws Exception {
        Path file = dir.resolve("old.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE profile (id INTEGER PRIMARY KEY CHECK (id = 1), birth_year INTEGER, province TEXT,
                      resident_since_year INTEGER, annual_income TEXT, rrsp_deduction_limit TEXT, fhsa_opened_year INTEGER)""");
            s.execute("INSERT INTO profile (id, birth_year) VALUES (1, 2003)");
        }
        DatabaseManager db = DatabaseManager.forFile(file);
        SqliteProfileRepository repo = new SqliteProfileRepository(db);
        UserProfile p = repo.load();
        assertEquals(2003, p.getBirthYear(), "old data is kept");
        assertNull(p.getPensionAdjustment());
        p.setPensionAdjustment(Money.of(1200));
        repo.save(p);
        assertEquals(Money.of(1200), new SqliteProfileRepository(DatabaseManager.forFile(file)).load().getPensionAdjustment());
    }
}
