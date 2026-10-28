package com.maplecfo.planning;

import com.maplecfo.automation.limits.LimitsService;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.persistence.ContributionRepository;
import com.maplecfo.persistence.ProfileRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * F06 TFSA / RRSP / FHSA contribution-room tracker.
 *
 * <ul>
 *   <li><b>TFSA:</b> the sum of the annual limits from the first eligible year
 *   (the latest of 2009, the year the user turned 18 and the year they became
 *   resident), plus withdrawals from earlier years, minus contributions.</li>
 *   <li><b>FHSA:</b> the annual limit plus unused room carried forward (capped),
 *   never more than the lifetime limit minus earlier contributions.</li>
 *   <li><b>RRSP:</b> the deduction limit from the user's CRA Notice of
 *   Assessment minus this year's contributions.</li>
 * </ul>
 * Limits come from {@link LimitsService}, so a new year needs no code change.
 */
public class RegisteredAccountService {

    private final LimitsService limitsService;
    private final ProfileRepository profileRepo;
    private final ContributionRepository contributionRepo;
    private final EventBus eventBus;

    public RegisteredAccountService(LimitsService limitsService, ProfileRepository profileRepo,
                                    ContributionRepository contributionRepo, EventBus eventBus) {
        this.limitsService = Objects.requireNonNull(limitsService);
        this.profileRepo = Objects.requireNonNull(profileRepo);
        this.contributionRepo = Objects.requireNonNull(contributionRepo);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * Room for one account type in one year.
     *
     * @throws IllegalStateException when the profile lacks required fields;
     *         call {@link #checkContribution} to get them listed instead
     */
    public ContributionRoom computeRoom(AccountType type, int year) {
        requireRegistered(type);
        UserProfile profile = profileRepo.load();
        List<String> missing = profile.missingFieldsFor(type);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Profile is missing: " + String.join(", ", missing));
        }
        ContributionLimits limits = limitsService.currentLimits();
        List<ContributionRecord> records = contributionRepo.findByType(type);
        return switch (type) {
            case TFSA -> tfsaRoom(profile, limits, records, year);
            case FHSA -> fhsaRoom(profile, limits, records, year);
            case RRSP -> rrspRoom(profile, records, year);
            default -> throw new IllegalArgumentException(type + " is not a registered account");
        };
    }

    public ContributionCheck checkContribution(AccountType type, Money amount, LocalDate date) {
        requireRegistered(type);
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(date, "date");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Contribution amount must be greater than zero");
        }
        List<String> missing = profileRepo.load().missingFieldsFor(type);
        if (!missing.isEmpty()) {
            return ContributionCheck.unknown(type, amount, missing);
        }
        if (type == AccountType.TFSA && !limitsService.currentLimits().hasTfsaLimitFor(date.getYear())) {
            return ContributionCheck.unknown(type, amount, List.of("TFSA limit for " + date.getYear()));
        }
        ContributionRoom room = computeRoom(type, date.getYear());
        if (amount.isGreaterThan(room.available())) {
            Money excess = amount.minus(room.available().max(Money.zero()));
            ContributionCheck over = new ContributionCheck(ContributionCheck.Status.OVER, type, amount, room,
                    excess, List.of());
            eventBus.publish(FinanceEvent.of(EventType.CONTRIBUTION_WARNING, over));
            return over;
        }
        return new ContributionCheck(ContributionCheck.Status.OK, type, amount, room, Money.zero(), List.of());
    }

    public void recordContribution(ContributionRecord r) {
        contributionRepo.add(Objects.requireNonNull(r, "record"));
    }

    /** Removes a logged record (used to undo a confirmed contribution). */
    public void removeContribution(ContributionRecord r) {
        contributionRepo.remove(Objects.requireNonNull(r, "record"));
    }

    /** Saves the RRSP deduction limit the user confirmed (from the CRA notice or the F14 estimate). */
    public void updateRrspLimit(Money limit) {
        Objects.requireNonNull(limit, "limit");
        if (limit.isNegative()) {
            throw new IllegalArgumentException("RRSP limit cannot be negative");
        }
        UserProfile profile = profileRepo.load();
        profile.setRrspDeductionLimit(limit);
        profileRepo.save(profile);
    }

    // ---------------------------------------------------------------- rules

    private ContributionRoom tfsaRoom(UserProfile p, ContributionLimits limits,
                                      List<ContributionRecord> records, int year) {
        int firstYear = Math.max(Math.max(limits.firstTfsaYear(), p.getBirthYear() + 18), p.getResidentSinceYear());
        Money created = Money.zero();
        for (int y = firstYear; y <= year; y++) {
            created = created.plus(limits.tfsaLimitFor(y));
        }
        Money contributed = Money.zero();
        Money restored = Money.zero();
        for (ContributionRecord r : records) {
            int y = r.date().getYear();
            if (r.kind() == ContributionKind.CONTRIBUTION && y <= year) {
                contributed = contributed.plus(r.amount());
            } else if (r.kind() == ContributionKind.WITHDRAWAL && y < year) {
                restored = restored.plus(r.amount()); // withdrawals come back next January
            }
        }
        Money limit = created.plus(restored);
        return new ContributionRoom(AccountType.TFSA, year, limit, contributed, limit.minus(contributed));
    }

    private ContributionRoom fhsaRoom(UserProfile p, ContributionLimits limits,
                                      List<ContributionRecord> records, int year) {
        int opened = Math.max(p.getFhsaOpenedYear(), limits.fhsaFirstYear());
        if (year < opened) {
            return new ContributionRoom(AccountType.FHSA, year, Money.zero(), Money.zero(), Money.zero());
        }
        Money carry = Money.zero();
        Money lifetimeUsed = Money.zero();
        for (int y = opened; ; y++) {
            Money contributedThisYear = contributionsIn(records, y);
            Money room = limits.fhsaAnnualLimit().plus(carry)
                    .min(limits.fhsaLifetimeLimit().minus(lifetimeUsed))
                    .max(Money.zero());
            if (y == year) {
                return new ContributionRoom(AccountType.FHSA, year, room, contributedThisYear,
                        room.minus(contributedThisYear));
            }
            carry = limits.fhsaAnnualLimit().plus(carry).minus(contributedThisYear)
                    .max(Money.zero()).min(limits.fhsaCarryForwardMax());
            lifetimeUsed = lifetimeUsed.plus(contributedThisYear);
        }
    }

    private ContributionRoom rrspRoom(UserProfile p, List<ContributionRecord> records, int year) {
        Money limit = p.getRrspDeductionLimit();
        Money used = contributionsIn(records, year);
        return new ContributionRoom(AccountType.RRSP, year, limit, used, limit.minus(used));
    }

    private static Money contributionsIn(List<ContributionRecord> records, int year) {
        Money total = Money.zero();
        for (ContributionRecord r : records) {
            if (r.kind() == ContributionKind.CONTRIBUTION && r.date().getYear() == year) {
                total = total.plus(r.amount());
            }
        }
        return total;
    }

    private static void requireRegistered(AccountType type) {
        if (type == null || !type.isRegistered()) {
            throw new IllegalArgumentException(type + " is not a registered account");
        }
    }
}
