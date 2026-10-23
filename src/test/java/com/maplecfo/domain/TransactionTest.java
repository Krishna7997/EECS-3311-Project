package com.maplecfo.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private Transaction tx(String id, String desc, String amount) {
        return new Transaction(id, LocalDate.of(2026, 10, 3), desc, null, Money.of(amount), "acc-1");
    }

    @Test
    void sameTransactionFromTwoExportsHasSameFingerprint() {
        Transaction a = tx("1", "NETFLIX.COM  ", "-16.99");
        Transaction b = tx("2", "Netflix.com", "-16.99");
        assertEquals(a.fingerprint(), b.fingerprint());
    }

    @Test
    void differentAmountGivesDifferentFingerprint() {
        assertNotEquals(tx("1", "Netflix", "-16.99").fingerprint(), tx("2", "Netflix", "-18.99").fingerprint());
    }

    @Test
    void expensesAreNegative() {
        assertTrue(tx("1", "Loblaws", "-54.20").isExpense());
        assertFalse(tx("2", "Payroll", "2100.00").isExpense());
    }

    @Test
    void newTransactionIsUncategorizedAndCanBeCategorized() {
        Transaction t = tx("1", "Tim Hortons", "-4.50");
        assertEquals(Category.UNCATEGORIZED, t.getCategory());
        assertEquals(CategorySource.NONE, t.getCategorySource());
        t.setCategory(Category.DINING, CategorySource.RULE);
        assertEquals(Category.DINING, t.getCategory());
        assertEquals("Tim Hortons", t.getMerchant(), "merchant defaults to the description");
    }

    @Test
    void categoryParsingAcceptsOnlyValidNames() {
        assertEquals(Category.GROCERIES, Category.parseOrNull(" groceries "));
        assertNull(Category.parseOrNull("Food & Drink"));
        assertNull(Category.parseOrNull(null));
    }
}
