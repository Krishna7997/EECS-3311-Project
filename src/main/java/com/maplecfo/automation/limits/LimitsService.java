package com.maplecfo.automation.limits;

import com.maplecfo.planning.ContributionLimits;

import java.util.List;
import java.util.Optional;

/**
 * Supplies the contribution limits used by room checks (F06).
 * Tries each {@link LimitsProvider} in order (ordered fallback: cache, then the
 * bundled file) and keeps the first result in memory. F15: {@link #refreshIfStale}
 * downloads newer limits, validates them, stores them in the cache and announces
 * LIMITS_UPDATED.
 */
public class LimitsService {

    private final List<LimitsProvider> providers;
    private ContributionLimits current;
    private String currentSource;

    public LimitsService(List<LimitsProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one limits provider is required");
        }
        this.providers = List.copyOf(providers);
    }

    /** A service that only uses the limits bundled with the app. */
    public static LimitsService bundledOnly() {
        return new LimitsService(List.of(new BundledLimitsProvider()));
    }

    public synchronized ContributionLimits currentLimits() {
        if (current == null) {
            reload();
        }
        return current;
    }

    /**
     * Asks the providers again, in order. The first answer is used, unless a later provider has newer
     * data (for example an app update that ships newer limits than an old cache).
     */
    public synchronized void reload() {
        ContributionLimits best = null;
        String bestSource = null;
        for (LimitsProvider p : providers) {
            Optional<ContributionLimits> limits = p.load();
            if (limits.isPresent() && (best == null || newer(limits.get(), best))) {
                best = limits.get();
                bestSource = p.name();
            }
        }
        if (best == null) {
            throw new IllegalStateException("No contribution limits available from any source");
        }
        current = best;
        currentSource = bestSource;
    }

    private static boolean newer(ContributionLimits a, ContributionLimits b) {
        int ra = a.rrspDollarLimits().keySet().stream().max(Integer::compare).orElse(0);
        int rb = b.rrspDollarLimits().keySet().stream().max(Integer::compare).orElse(0);
        return a.latestTfsaYear() > b.latestTfsaYear() || (a.latestTfsaYear() == b.latestTfsaYear() && ra > rb);
    }

    // ------------------------------------------------------------------ F15 refresh

    private RemoteLimitsProvider remote;
    private LimitsCache cache;
    private com.maplecfo.events.EventBus eventBus;
    private java.time.Clock clock = java.time.Clock.systemDefaultZone();
    private final LimitsValidator validator = new LimitsValidator();

    /** Turns on F15 refreshing. The cache should also be the first provider passed to the constructor. */
    public synchronized void configureRefresh(RemoteLimitsProvider remote, LimitsCache cache,
                                              com.maplecfo.events.EventBus eventBus, java.time.Clock clock) {
        this.remote = remote;
        this.cache = cache;
        this.eventBus = eventBus;
        this.clock = clock;
    }

    /**
     * Downloads the latest limits when the last check is older than {@code maxAge}. Never throws: on any
     * problem the current limits stay in use and the status says why.
     */
    public synchronized LimitsStatus refreshIfStale(java.time.Duration maxAge) {
        ContributionLimits before = currentLimits();
        if (remote == null || cache == null) {
            return new LimitsStatus(LimitsStatus.State.USING_CACHED, currentSource, "Online updates are off.");
        }
        Optional<java.time.Instant> last = cache.lastChecked();
        if (last.isPresent() && last.get().plus(maxAge).isAfter(clock.instant())) {
            return new LimitsStatus(LimitsStatus.State.UP_TO_DATE, currentSource,
                    "Limits checked recently (" + last.get() + ").");
        }
        String json;
        ContributionLimits next;
        try {
            json = remote.fetch();
            next = ContributionLimits.loadFrom(new java.io.ByteArrayInputStream(
                    json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.io.IOException | RuntimeException e) {
            return new LimitsStatus(LimitsStatus.State.USING_CACHED, currentSource,
                    "Could not download new limits (" + e.getMessage() + "); using " + currentSource + " limits.");
        }
        List<String> problems = validator.validate(next, before, java.time.LocalDate.now(clock).getYear());
        if (!problems.isEmpty()) {
            return new LimitsStatus(LimitsStatus.State.USING_CACHED, currentSource,
                    "Downloaded limits were rejected: " + String.join("; ", problems));
        }
        try {
            boolean changed = !sameLimits(next, before);
            cache.save(json);
            if (!changed) {
                return new LimitsStatus(LimitsStatus.State.UP_TO_DATE, currentSource, "Limits are up to date.");
            }
            current = next;
            currentSource = cache.name();
            String msg = "Contribution limits updated: TFSA " + next.latestTfsaYear() + " limit is "
                    + next.tfsaLimitFor(next.latestTfsaYear()) + ".";
            if (eventBus != null) {
                eventBus.publish(com.maplecfo.events.FinanceEvent.of(com.maplecfo.events.EventType.LIMITS_UPDATED, msg));
            }
            return new LimitsStatus(LimitsStatus.State.UPDATED, currentSource, msg);
        } catch (java.io.IOException e) {
            return new LimitsStatus(LimitsStatus.State.USING_CACHED, currentSource,
                    "Could not save the new limits: " + e.getMessage());
        }
    }

    private static boolean sameLimits(ContributionLimits a, ContributionLimits b) {
        return a.tfsaAnnualLimits().equals(b.tfsaAnnualLimits()) && a.rrspDollarLimits().equals(b.rrspDollarLimits())
                && a.fhsaAnnualLimit().equals(b.fhsaAnnualLimit()) && a.fhsaLifetimeLimit().equals(b.fhsaLifetimeLimit());
    }

    /** Name of the provider the current limits came from, e.g. "bundled". */
    public synchronized String currentSource() {
        currentLimits();
        return currentSource;
    }
}
