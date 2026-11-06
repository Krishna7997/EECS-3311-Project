package com.maplecfo.planning;

import com.maplecfo.agent.AgentResponse;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** F19: where extra money could go, in the order commonly taught (all numbers from Java). */
class ExtraMoneyPlannerTest {

    @TempDir
    Path dir;

    private MapleCfoFacade app;

    @BeforeEach
    void setUp() throws Exception {
        app = AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")),
                Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneId.of("America/Toronto")));
        app.importTransactionsAuto(Path.of("sample-data/demo/td_chequing_2025-2026.csv"), "chequing");
        UserProfile p = app.getProfile();
        p.setBirthYear(2001);
        p.setResidentSinceYear(2001);
        p.setRrspDeductionLimit(Money.of(5000));
        p.setFhsaOpenedYear(2026);
        app.saveProfile(p);
        app.addDebt("RBC Visa", Money.of(1000), 20.99, Money.of(30));
        app.addDebt("Student loan", Money.of(6000), 6.2, Money.of(95));
    }

    private List<String> targets(ExtraMoneyPlanner.Plan p) {
        return p.steps().stream().map(ExtraMoneyPlanner.Step::target).toList();
    }

    @Test
    void highInterestDebtComesFirst() {
        ExtraMoneyPlanner.Plan p = app.planExtraMoney(Money.of(600));
        assertEquals(List.of("Pay down RBC Visa"), targets(p));
        assertEquals(Money.of(600), p.steps().get(0).amount());
        assertTrue(p.steps().get(0).reason().contains("$125.94 of interest a year"), p.steps().get(0).reason());
        assertEquals(Money.zero(), p.leftover());
    }

    @Test
    void thenTheEmergencyFundWhenCashIsLow() {
        app.addAccount(new Account("chq", "TD Chequing", AccountType.CHEQUING, "TD", Money.of(500)));
        ExtraMoneyPlanner.Plan p = app.planExtraMoney(Money.of(3000));
        assertEquals(List.of("Pay down RBC Visa", "Emergency fund (savings)"), targets(p));
        assertEquals(Money.of(1000), p.steps().get(0).amount(), "never more than the balance owed");
        assertEquals(Money.of(2000), p.steps().get(1).amount());
        assertTrue(p.summary().startsWith("A common order for $3,000.00: $1,000.00 to pay down RBC Visa"), p.summary());
    }

    @Test
    void withEnoughCashTheRoomIsUsedFhsaTfsaRrspThenCheapDebt() {
        app.addAccount(new Account("sav", "Savings", AccountType.SAVINGS, "EQ", Money.of(100000)));
        ExtraMoneyPlanner.Plan p = app.planExtraMoney(Money.of(200000));
        assertEquals(List.of("Pay down RBC Visa", "Contribute to your FHSA", "Contribute to your TFSA",
                "Contribute to your RRSP", "Pay down Student loan"), targets(p));
        assertEquals(Money.of(8000), p.steps().get(1).amount(), "FHSA opened this year: $8,000 room");
        assertEquals(Money.of(5000), p.steps().get(3).amount(), "RRSP room from the profile");
        assertTrue(p.leftover().isPositive());
        assertTrue(p.facts().stream().anyMatch(f -> f.startsWith("TFSA room left in 2026")));
    }

    @Test
    void invalidAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> app.planExtraMoney(Money.zero()));
    }

    @Test
    void theAgentUsesTheToolInDemoMode() {
        AgentResponse r = app.explainExtraMoney(Money.of(600));
        assertEquals(List.of("extra_money_plan"), r.toolsUsed());
        assertTrue(r.answer().contains("$600.00 to pay down RBC Visa"), r.answer());
        assertTrue(app.askCfo("Where should I put an extra $600?").toolsUsed().contains("extra_money_plan"));
    }
}
