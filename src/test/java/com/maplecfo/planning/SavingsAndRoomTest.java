package com.maplecfo.planning;

import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.events.EventType;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.reports.PeriodSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Saved this month / this year, account activity, and the RRSP $2,000 allowance. */
class SavingsAndRoomTest {

    @TempDir
    Path dir;
    private MapleCfoFacade app;

    @BeforeEach
    void setUp() throws Exception {
        Clock c = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneId.of("America/Toronto"));
        app = AppContext.create(DatabaseManager.forFile(dir.resolve("s.db")), c);
        app.importTransactions(Path.of("sample-data/td_chequing_sample.csv"), "chequing", "TD bank account");
    }

    @Test
    void savedThisMonthAndThisYear() {
        PeriodSummary oct = app.summarize(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21));
        assertEquals(Money.of("1890.00"), oct.income());
        assertEquals(Money.of("2256.64"), oct.spending());
        assertEquals(Money.of("-366.64"), oct.saved());

        PeriodSummary sep = app.summarize(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertEquals(Money.of(500), sep.toRegistered(), "the September TFSA transfer is saving, not spending");
        assertEquals(37, sep.savingsRatePercent());

        PeriodSummary year = app.summarize(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 21));
        assertEquals(year.income().minus(year.spending()), year.saved());
        assertEquals(Money.of(2000), year.toRegistered(), "four $500 TFSA transfers, June to September");
        assertEquals(Money.zero(), year.fromRegistered());
        assertEquals(Money.zero(), app.summarize(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)).income());
    }

    @Test
    void accountActivityIsNewestFirst() {
        List<Transaction> chq = app.getAccountActivity("chequing");
        assertEquals(136, chq.size());
        assertFalse(chq.get(0).getDate().isBefore(chq.get(chq.size() - 1).getDate()));
        assertTrue(app.getAccountActivity("visa").isEmpty());
    }

    @Test
    void contributionTotalsForTheYear() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, null, null));
        app.recordContribution(new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 3, 1), Money.of(500),
                ContributionKind.CONTRIBUTION));
        app.recordContribution(new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 5, 1), Money.of(200),
                ContributionKind.WITHDRAWAL));
        app.recordContribution(new ContributionRecord(AccountType.TFSA, LocalDate.of(2025, 5, 1), Money.of(900),
                ContributionKind.CONTRIBUTION));
        Money[] t = app.getContributionTotals(AccountType.TFSA, 2026);
        assertEquals(Money.of(500), t[0]);
        assertEquals(Money.of(200), t[1]);
        assertEquals(3, app.getContributions(AccountType.TFSA).size());
        assertEquals(LocalDate.of(2026, 5, 1), app.getContributions(AccountType.TFSA).get(0).date(), "newest first");
    }

    @Test
    void rrspHasATwoThousandDollarAllowance() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, Money.of(5400), null));
        int alertsBefore = app.getAlerts().size();

        assertEquals(ContributionCheck.Status.OK, app.previewContribution(AccountType.RRSP, Money.of(5000)).status());
        ContributionCheck allowance = app.previewContribution(AccountType.RRSP, Money.of(6000));
        assertEquals(ContributionCheck.Status.ALLOWANCE, allowance.status());
        assertEquals(Money.of(600), allowance.excess());
        assertEquals(ContributionCheck.Status.ALLOWANCE, app.previewContribution(AccountType.RRSP, Money.of(7400)).status(),
                "exactly $2,000 over is still allowed");
        assertEquals(ContributionCheck.Status.OVER, app.previewContribution(AccountType.RRSP, Money.of(7401)).status());
        assertEquals(alertsBefore, app.getAlerts().size(), "a preview while typing never raises alerts");

        assertEquals(ContributionCheck.Status.OVER, app.checkContribution(AccountType.RRSP, Money.of(9000)).status());
        assertTrue(app.getAlerts().stream().anyMatch(a -> a.source() == EventType.CONTRIBUTION_WARNING));

        app.recordContribution(new ContributionRecord(AccountType.RRSP, LocalDate.of(2026, 10, 1), Money.of(6000),
                ContributionKind.CONTRIBUTION));
        ContributionRoom r = app.getContributionRoom(AccountType.RRSP, 2026);
        assertTrue(r.isOverLimit());
        assertFalse(r.isOverContributed(), "inside the allowance there is no tax");
    }

    @Test
    void tfsaHasNoAllowance() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, null, null));
        Money room = app.getContributionRoom(AccountType.TFSA, 2026).available();
        assertEquals(ContributionCheck.Status.OVER, app.previewContribution(AccountType.TFSA, room.plus(Money.of(1))).status());
    }

    @Test
    void withdrawingTheExcessClearsAnOverContribution() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, null, null));
        Money room = app.getContributionRoom(AccountType.TFSA, 2026).available();
        app.recordContribution(new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 3, 1),
                room.plus(Money.of(1000)), ContributionKind.CONTRIBUTION));
        ContributionRoom over = app.getContributionRoom(AccountType.TFSA, 2026);
        assertEquals(Money.of(-1000), over.available());
        assertTrue(over.isOverContributed());

        app.recordContribution(new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 4, 1), Money.of(400),
                ContributionKind.WITHDRAWAL));
        assertEquals(Money.of(-600), app.getContributionRoom(AccountType.TFSA, 2026).available(),
                "part of the excess is gone");

        app.recordContribution(new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 5, 1), Money.of(1600),
                ContributionKind.WITHDRAWAL));
        ContributionRoom fixed = app.getContributionRoom(AccountType.TFSA, 2026);
        assertEquals(Money.zero(), fixed.available(), "no longer over, but no new room until January");
        assertFalse(fixed.isOverContributed());
    }

    @Test
    void aWithdrawalWithoutExcessGivesRoomBackOnlyNextYear() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, null, null));
        Money before = app.getContributionRoom(AccountType.TFSA, 2026).available();
        app.recordContribution(new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 3, 1), Money.of(5000),
                ContributionKind.CONTRIBUTION));
        app.recordContribution(new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 6, 1), Money.of(2000),
                ContributionKind.WITHDRAWAL));
        assertEquals(before.minus(Money.of(5000)), app.getContributionRoom(AccountType.TFSA, 2026).available());
    }

    @Test
    void rrspExcessAlsoGoesAwayWhenWithdrawn() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, Money.of(5000), null));
        app.recordContribution(new ContributionRecord(AccountType.RRSP, LocalDate.of(2026, 2, 1), Money.of(8000),
                ContributionKind.CONTRIBUTION));
        assertTrue(app.getContributionRoom(AccountType.RRSP, 2026).isOverContributed());
        app.recordContribution(new ContributionRecord(AccountType.RRSP, LocalDate.of(2026, 3, 1), Money.of(3000),
                ContributionKind.WITHDRAWAL));
        assertEquals(Money.zero(), app.getContributionRoom(AccountType.RRSP, 2026).available());
    }

    // ------------------------------------------------------------------ HBP / LLP / FHSA transfer / pension adjustment

    @Test
    void homeBuyersPlanIsRepaidOverFifteenYearsWithoutUsingRoom() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, Money.of(5000), null));
        app.recordContribution(new ContributionRecord(AccountType.RRSP, LocalDate.of(2020, 6, 1), Money.of(15000),
                ContributionKind.HBP_WITHDRAWAL));
        app.recordContribution(new ContributionRecord(AccountType.RRSP, LocalDate.of(2026, 2, 1), Money.of(1000),
                ContributionKind.HBP_REPAYMENT));
        assertEquals(Money.of(5000), app.getContributionRoom(AccountType.RRSP, 2026).available(),
                "neither the HBP withdrawal nor the repayment uses room");

        RepaymentPlan p = app.getRepaymentPlans().get(0);
        assertEquals("Home Buyers' Plan", p.program());
        assertEquals(2022, p.firstRepaymentYear(), "second year after a 2020 withdrawal");
        assertEquals(Money.of(1000), p.yearlyPayment());
        assertEquals(Money.of(14000), p.balance());
        assertEquals(Money.of(4000), p.dueThisYear(), "2022-2026 = 5 payments of $1,000, $1,000 paid");
        assertTrue(p.summary(2026).contains("Repay $4,000.00 for 2026"));
    }

    @Test
    void hbpWithdrawalsIn2022To2025StartLaterAndTheLimitIsChecked() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, Money.of(5000), null));
        app.recordContribution(new ContributionRecord(AccountType.RRSP, LocalDate.of(2024, 6, 1), Money.of(65000),
                ContributionKind.HBP_WITHDRAWAL));
        RepaymentPlan p = app.getRepaymentPlans().get(0);
        assertEquals(2029, p.firstRepaymentYear(), "temporary 3-year deferral");
        assertEquals(Money.zero(), p.dueThisYear());
        assertFalse(p.warnings().isEmpty(), "over the $60,000 limit");
    }

    @Test
    void kindsOnlyWhereTheyApply() {
        assertThrows(IllegalArgumentException.class, () -> new ContributionRecord(AccountType.TFSA, LocalDate.now(),
                Money.of(100), ContributionKind.HBP_WITHDRAWAL));
        assertThrows(IllegalArgumentException.class, () -> new ContributionRecord(AccountType.RRSP, LocalDate.now(),
                Money.of(100), ContributionKind.TRANSFER_TO_RRSP));
    }

    @Test
    void fhsaToRrspTransferLeavesTheFhsaWithoutRestoringRoom() {
        app.saveProfile(new UserProfile(2003, "ON", 2003, null, Money.of(5000), 2026));
        app.recordContribution(new ContributionRecord(AccountType.FHSA, LocalDate.of(2026, 1, 10), Money.of(8000),
                ContributionKind.CONTRIBUTION));
        app.recordContribution(new ContributionRecord(AccountType.FHSA, LocalDate.of(2026, 6, 1), Money.of(3000),
                ContributionKind.TRANSFER_TO_RRSP));
        assertEquals(Money.zero(), app.getContributionRoom(AccountType.FHSA, 2026).available());
        assertEquals(Money.of(5000), app.getContributionRoom(AccountType.RRSP, 2026).available(),
                "the transfer does not use RRSP room");
        assertEquals(Money.of(3000), app.getContributionTotals(AccountType.FHSA, 2026)[1]);
    }

    @Test
    void pensionAdjustmentLowersTheRrspEstimateAndIsSaved() {
        UserProfile p = new UserProfile(2003, "ON", 2003, null, null, null);
        p.setPensionAdjustment(Money.of(3000));
        app.saveProfile(p);
        assertEquals(Money.of(3000), app.getProfile().getPensionAdjustment(), "stored in the database");
        var est = app.estimateRrspRoom(2027);
        assertEquals(Money.of("5164.80"), est.estimate(), "18% of $45,360 = $8,164.80, minus $3,000");
        assertTrue(est.explanation().contains("pension adjustment"));
    }
}
