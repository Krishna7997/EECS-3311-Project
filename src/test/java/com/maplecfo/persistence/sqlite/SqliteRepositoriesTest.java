package com.maplecfo.persistence.sqlite;

import com.maplecfo.categorization.CategoryRule;
import com.maplecfo.domain.*;
import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.DefaultProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class SqliteRepositoriesTest {

    @TempDir
    Path dir;
    private DatabaseManager db;

    @BeforeEach
    void setUp() {
        db = DatabaseManager.forFile(dir.resolve("data/test.db"));
    }

    @Test
    void transactionsRoundTripWithCategoryAndFingerprint() {
        SqliteTransactionRepository repo = new SqliteTransactionRepository(db);
        Transaction t = new Transaction("t1", LocalDate.of(2026, 10, 3), "NETFLIX.COM", null, Money.of("-16.99"), "chq");
        t.setCategory(Category.SUBSCRIPTIONS, CategorySource.RULE);
        repo.save(t);

        Transaction back = repo.findById("t1").orElseThrow();
        assertEquals(Money.of("-16.99"), back.getAmount());
        assertEquals(Category.SUBSCRIPTIONS, back.getCategory());
        assertEquals(CategorySource.RULE, back.getCategorySource());
        assertEquals(1, repo.countByFingerprint(t.fingerprint()));
        assertEquals(1, repo.findByMonth(YearMonth.of(2026, 10)).size());
        assertTrue(repo.findByMonth(YearMonth.of(2026, 9)).isEmpty());

        repo.delete("t1");
        assertTrue(repo.findAll().isEmpty());
    }

    @Test
    void dataSurvivesReopeningTheDatabase() {
        new SqliteBudgetRepository(db).save(new Budget(Category.DINING, YearMonth.of(2026, 10), Money.of(250)));
        DatabaseManager reopened = DatabaseManager.forFile(dir.resolve("data/test.db"));
        assertEquals(Money.of(250), new SqliteBudgetRepository(reopened)
                .find(Category.DINING, YearMonth.of(2026, 10)).orElseThrow().limit());
    }

    @Test
    void budgetsReplaceByCategoryAndMonth() {
        SqliteBudgetRepository repo = new SqliteBudgetRepository(db);
        YearMonth oct = YearMonth.of(2026, 10);
        repo.save(new Budget(Category.DINING, oct, Money.of(200)));
        repo.save(new Budget(Category.DINING, oct, Money.of(300)));
        assertEquals(1, repo.findByMonth(oct).size());
        repo.delete(Category.DINING, oct);
        assertTrue(repo.findByMonth(oct).isEmpty());
    }

    @Test
    void accountsDebtsAndContributions() {
        SqliteAccountRepository accounts = new SqliteAccountRepository(db);
        accounts.save(new Account("a1", "Visa", AccountType.CREDIT_CARD, "RBC", Money.of(420)));
        assertEquals(Money.of(-420), accounts.findById("a1").orElseThrow().getValue());

        SqliteDebtRepository debts = new SqliteDebtRepository(db);
        debts.save(new Debt("d1", "Visa", Money.of(420), 20.99, Money.of(25), "a1"));
        assertEquals(20.99, debts.findAll().get(0).getApr(), 1e-9);

        SqliteContributionRepository contributions = new SqliteContributionRepository(db);
        ContributionRecord r = new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 1, 5), Money.of(500),
                ContributionKind.CONTRIBUTION);
        contributions.add(r);
        contributions.add(r);
        contributions.remove(r);
        assertEquals(1, contributions.findByType(AccountType.TFSA).size(), "remove deletes only one matching row");
    }

    @Test
    void profileStartsEmptyAndKeepsNulls() {
        SqliteProfileRepository repo = new SqliteProfileRepository(db);
        assertNull(repo.load().getBirthYear());
        UserProfile p = new UserProfile(2003, "ON", 2010, null, Money.of(5000), null);
        repo.save(p);
        UserProfile back = repo.load();
        assertEquals(2003, back.getBirthYear());
        assertEquals(Money.of(5000), back.getRrspDeductionLimit());
        assertNull(back.getAnnualIncome());
        assertNull(back.getFhsaOpenedYear());
    }

    @Test
    void mappingProfilesAndLearnedRules() {
        SqliteMappingProfileRepository profiles = new SqliteMappingProfileRepository(db);
        profiles.save(DefaultProfiles.TD_BANK);
        ColumnMapping back = profiles.findByName("td BANK account").orElseThrow();
        assertEquals(DefaultProfiles.TD_BANK, back);

        SqliteCategoryRuleRepository rules = new SqliteCategoryRuleRepository(db);
        rules.save(new CategoryRule("costco wholesale", Category.SHOPPING, true));
        rules.save(new CategoryRule("costco wholesale", Category.GROCERIES, true));
        assertEquals(1, rules.findAll().size());
        assertEquals(Category.GROCERIES, rules.findAll().get(0).category());
    }
}
