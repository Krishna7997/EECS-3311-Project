package com.maplecfo.planning;

import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** F07: FIRE starts from your own numbers and remembers the last plan. */
class FireFromMyDataTest {

    @TempDir
    Path dir;

    private MapleCfoFacade app() {
        return AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")),
                Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneId.of("America/Toronto")));
    }

    @Test
    void myNumbersComeFromInvestmentAccountsAndCashFlow() throws Exception {
        MapleCfoFacade app = app();
        assertThrows(IllegalStateException.class, app::suggestFireInputs, "no history yet");
        app.importTransactionsAuto(Path.of("sample-data/demo/td_chequing_2025-2026.csv"), "chequing");
        app.addAccount(new Account("tfsa", "TFSA", AccountType.TFSA, "WS", Money.of(30000)));
        app.addAccount(new Account("rrsp", "RRSP", AccountType.RRSP, "WS", Money.of(3000)));
        app.addAccount(new Account("chq", "Chequing", AccountType.CHEQUING, "TD", Money.of(5000)));
        UserProfile p = app.getProfile();
        p.setBirthYear(2001);
        app.saveProfile(p);

        FireInputs in = app.suggestFireInputs();
        assertEquals(Money.of(33000), in.invested(), "TFSA + RRSP, not chequing");
        assertEquals(25, in.currentAge());
        assertTrue(in.annualSpending().isPositive());
        assertFalse(in.yearlySaving().isNegative());
    }

    @Test
    void theLastPlanIsRemembered() {
        MapleCfoFacade app = app();
        assertTrue(app.lastFireInputs().isEmpty());
        FireInputs in = new FireInputs(Money.of(1000), Money.of(12000), Money.of(36000), 0.045, 0.035, 25,
                Money.of(900000), 50);
        app.projectFire(in);
        assertEquals(in, app.lastFireInputs().orElseThrow());
        assertEquals(in, app().lastFireInputs().orElseThrow(), "kept in the database");
    }
}
