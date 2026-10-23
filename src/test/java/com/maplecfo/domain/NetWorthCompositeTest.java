package com.maplecfo.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetWorthCompositeTest {

    @Test
    void liabilitiesCountNegatively() {
        Account card = new Account("a1", "Visa", AccountType.CREDIT_CARD, "TD", Money.of(500));
        assertTrue(card.isLiability());
        assertEquals(Money.of(-500), card.getValue());
    }

    @Test
    void groupValueIsSumOfNestedChildren() {
        AccountGroup root = new AccountGroup("Net worth");
        AccountGroup registered = new AccountGroup("Registered");
        registered.add(new Account("t", "TFSA", AccountType.TFSA, "WS", Money.of(7000)));
        registered.add(new Account("r", "RRSP", AccountType.RRSP, "WS", Money.of(3000)));
        root.add(registered);
        root.add(new Account("c", "Chequing", AccountType.CHEQUING, "TD", Money.of("1250.75")));
        root.add(new Account("l", "Student loan", AccountType.LOAN, "NSLSC", Money.of(9000)));

        assertEquals(Money.of(10000), registered.getValue());
        assertEquals(Money.of("2250.75"), root.getValue());
        assertEquals(3, root.getChildren().size());
    }

    @Test
    void updatingAnAccountChangesTheTotal() {
        AccountGroup root = new AccountGroup("Net worth");
        Account chequing = new Account("c", "Chequing", AccountType.CHEQUING, "TD", Money.of(100));
        root.add(chequing);
        chequing.updateBalance(Money.of(250));
        assertEquals(Money.of(250), root.getValue());
    }

    @Test
    void groupCannotContainItselfAndChildrenAreReadOnly() {
        AccountGroup g = new AccountGroup("g");
        assertThrows(IllegalArgumentException.class, () -> g.add(g));
        assertThrows(UnsupportedOperationException.class, () -> g.getChildren().add(g));
    }

    @Test
    void emptyGroupIsWorthZero() {
        assertEquals(Money.zero(), new AccountGroup("empty").getValue());
    }
}
