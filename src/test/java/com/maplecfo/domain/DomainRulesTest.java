package com.maplecfo.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class DomainRulesTest {

    @Test
    void budgetStatusMath() {
        Budget b = new Budget(Category.DINING, YearMonth.of(2026, 10), Money.of(200));
        BudgetStatus s = new BudgetStatus(b, Money.of(170));
        assertEquals(Money.of(30), s.remaining());
        assertEquals(85.0, s.percentUsed(), 0.001);
        assertFalse(s.isExceeded());
        assertTrue(new BudgetStatus(b, Money.of("200.01")).isExceeded());
    }

    @Test
    void budgetRejectsInvalidValues() {
        YearMonth m = YearMonth.of(2026, 10);
        assertThrows(IllegalArgumentException.class, () -> new Budget(Category.DINING, m, Money.zero()));
        assertThrows(IllegalArgumentException.class, () -> new Budget(Category.INCOME, m, Money.of(10)));
    }

    @Test
    void savingsGoalProgressAndDeposits() {
        SavingsGoal g = new SavingsGoal("g1", "Emergency fund", Money.of(5000), Money.of(1000),
                LocalDate.of(2027, 6, 1), null);
        assertEquals(0.2, g.progress(), 1e-9);
        g.deposit(Money.of(4500));
        assertEquals(1.0, g.progress(), 1e-9, "progress is capped at 100%");
        assertTrue(g.isComplete());
        assertEquals(Money.zero(), g.remaining());
        assertThrows(IllegalArgumentException.class, () -> g.deposit(Money.of(-1)));
    }

    @Test
    void debtValidationAndInterest() {
        Debt card = new Debt("d1", "Visa", Money.of(1200), 24.0, Money.of(40), null);
        assertEquals(Money.of(24), card.monthlyInterest());
        assertThrows(IllegalArgumentException.class,
                () -> new Debt("d2", "Bad", Money.of(100), 75.0, Money.of(10), null));
    }

    @Test
    void contributionRecordsOnlyForRegisteredAccounts() {
        LocalDate d = LocalDate.of(2026, 1, 15);
        assertDoesNotThrow(() -> new ContributionRecord(AccountType.TFSA, d, Money.of(500), ContributionKind.CONTRIBUTION));
        assertThrows(IllegalArgumentException.class,
                () -> new ContributionRecord(AccountType.CHEQUING, d, Money.of(500), ContributionKind.CONTRIBUTION));
        assertThrows(IllegalArgumentException.class,
                () -> new ContributionRecord(AccountType.TFSA, d, Money.zero(), ContributionKind.CONTRIBUTION));
    }

    @Test
    void profileListsMissingFields() {
        UserProfile p = new UserProfile();
        assertEquals(java.util.List.of("birthYear", "residentSinceYear"), p.missingFieldsFor(AccountType.TFSA));
        p.setBirthYear(2003);
        p.setResidentSinceYear(2015);
        assertTrue(p.missingFieldsFor(AccountType.TFSA).isEmpty());
        assertEquals(java.util.List.of("rrspDeductionLimit"), p.missingFieldsFor(AccountType.RRSP));
    }
}
