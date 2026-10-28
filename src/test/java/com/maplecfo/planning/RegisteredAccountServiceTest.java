package com.maplecfo.planning;

import com.maplecfo.automation.limits.LimitsService;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.events.EventBus;
import com.maplecfo.persistence.memory.InMemoryContributionRepository;
import com.maplecfo.persistence.memory.InMemoryProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RegisteredAccountServiceTest {

    private InMemoryProfileRepository profiles;
    private InMemoryContributionRepository contributions;
    private RegisteredAccountService service;

    @BeforeEach
    void setUp() {
        profiles = new InMemoryProfileRepository();
        contributions = new InMemoryContributionRepository();
        service = new RegisteredAccountService(LimitsService.bundledOnly(), profiles, contributions, new EventBus());
    }

    private void profile(Integer birthYear, Integer residentSince, Long rrspLimit, Integer fhsaOpened) {
        profiles.save(new UserProfile(birthYear, "ON", residentSince, null,
                rrspLimit == null ? null : Money.of(rrspLimit), fhsaOpened));
    }

    private void log(AccountType t, String date, long amount, ContributionKind kind) {
        contributions.add(new ContributionRecord(t, LocalDate.parse(date), Money.of(amount), kind));
    }

    @Test
    void tfsaRoomForSomeoneEligibleSince2009Is109000() {
        profile(1980, 1990, null, null);
        assertEquals(Money.of(109_000), service.computeRoom(AccountType.TFSA, 2026).available());
    }

    @Test
    void tfsaStartsTheYearYouTurn18() {
        profile(2003, 2003, null, null); // turned 18 in 2021: 6000 + 6000 + 6500 + 7000 + 7000 + 7000
        assertEquals(Money.of(39_500), service.computeRoom(AccountType.TFSA, 2026).limit());
    }

    @Test
    void tfsaStartsTheYearYouBecameResident() {
        profile(1995, 2024, null, null); // 2024, 2025, 2026
        assertEquals(Money.of(21_000), service.computeRoom(AccountType.TFSA, 2026).limit());
    }

    @Test
    void tfsaWithdrawalsComeBackTheNextYear() {
        profile(1995, 2024, null, null);
        log(AccountType.TFSA, "2025-03-01", 10_000, ContributionKind.CONTRIBUTION);
        log(AccountType.TFSA, "2025-09-01", 4_000, ContributionKind.WITHDRAWAL);
        log(AccountType.TFSA, "2026-09-01", 1_000, ContributionKind.WITHDRAWAL);
        ContributionRoom room = service.computeRoom(AccountType.TFSA, 2026);
        // 21,000 created + 4,000 restored (2025 withdrawal) - 10,000 contributed; the 2026 withdrawal is restored in 2027
        assertEquals(Money.of(15_000), room.available());
    }

    @Test
    void checkWarnsBeforeOverContribution() {
        profile(1995, 2024, null, null);
        log(AccountType.TFSA, "2026-01-10", 20_000, ContributionKind.CONTRIBUTION);
        ContributionCheck ok = service.checkContribution(AccountType.TFSA, Money.of(1_000), LocalDate.of(2026, 10, 1));
        assertEquals(ContributionCheck.Status.OK, ok.status());
        assertEquals(Money.zero(), ok.roomAfter());

        ContributionCheck over = service.checkContribution(AccountType.TFSA, Money.of(3_000), LocalDate.of(2026, 10, 1));
        assertEquals(ContributionCheck.Status.OVER, over.status());
        assertEquals(Money.of(2_000), over.excess());
    }

    @Test
    void incompleteProfileGivesUnknownWithMissingFields() {
        ContributionCheck c = service.checkContribution(AccountType.TFSA, Money.of(500), LocalDate.of(2026, 5, 1));
        assertEquals(ContributionCheck.Status.UNKNOWN, c.status());
        assertEquals(java.util.List.of("birthYear", "residentSinceYear"), c.missingFields());
    }

    @Test
    void yearWithoutKnownLimitIsUnknown() {
        profile(1995, 2024, null, null);
        ContributionCheck c = service.checkContribution(AccountType.TFSA, Money.of(500), LocalDate.of(2027, 1, 5));
        assertEquals(ContributionCheck.Status.UNKNOWN, c.status());
    }

    @Test
    void rrspRoomIsDeductionLimitMinusThisYearsContributions() {
        profile(null, null, 12_500L, null);
        log(AccountType.RRSP, "2026-02-01", 2_500, ContributionKind.CONTRIBUTION);
        log(AccountType.RRSP, "2025-02-01", 9_999, ContributionKind.CONTRIBUTION);
        assertEquals(Money.of(10_000), service.computeRoom(AccountType.RRSP, 2026).available());
    }

    @Test
    void updatingRrspLimitIsSavedToTheProfile() {
        service.updateRrspLimit(Money.of(8_400));
        assertEquals(Money.of(8_400), profiles.load().getRrspDeductionLimit());
        assertThrows(IllegalArgumentException.class, () -> service.updateRrspLimit(Money.of(-1)));
    }

    @Test
    void fhsaCarriesForwardUnusedRoomUpTo8000() {
        profile(null, null, null, 2024);
        // 2024: nothing contributed -> 8,000 carried to 2025 (room 16,000)
        assertEquals(Money.of(16_000), service.computeRoom(AccountType.FHSA, 2025).limit());
        // 2025: nothing again -> carry is still capped at 8,000
        assertEquals(Money.of(16_000), service.computeRoom(AccountType.FHSA, 2026).limit());
    }

    @Test
    void fhsaLifetimeLimitCapsTheRoom() {
        profile(null, null, null, 2023);
        log(AccountType.FHSA, "2023-06-01", 8_000, ContributionKind.CONTRIBUTION);
        log(AccountType.FHSA, "2024-06-01", 8_000, ContributionKind.CONTRIBUTION);
        log(AccountType.FHSA, "2025-06-01", 8_000, ContributionKind.CONTRIBUTION);
        log(AccountType.FHSA, "2026-06-01", 8_000, ContributionKind.CONTRIBUTION);
        // 32,000 used; only 8,000 lifetime room is left in 2027
        assertEquals(Money.of(8_000), service.computeRoom(AccountType.FHSA, 2027).limit());
    }

    @Test
    void invalidInputsAreRejected() {
        profile(1995, 2024, null, null);
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> service.checkContribution(AccountType.TFSA, Money.of(-5), d));
        assertThrows(IllegalArgumentException.class, () -> service.checkContribution(AccountType.CHEQUING, Money.of(5), d));
    }

    @Test
    void overContributionPublishesAWarningEvent() {
        EventBus bus = new EventBus();
        java.util.List<com.maplecfo.events.FinanceEvent> seen = new java.util.ArrayList<>();
        bus.subscribe(com.maplecfo.events.EventType.CONTRIBUTION_WARNING, seen::add);
        RegisteredAccountService s = new RegisteredAccountService(LimitsService.bundledOnly(), profiles, contributions, bus);
        profile(1995, 2026, null, null);
        s.checkContribution(AccountType.TFSA, Money.of(8_000), LocalDate.of(2026, 3, 1));
        assertEquals(1, seen.size());
    }
}
