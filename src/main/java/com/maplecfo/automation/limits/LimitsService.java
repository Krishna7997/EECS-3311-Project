package com.maplecfo.automation.limits;

import com.maplecfo.planning.ContributionLimits;

import java.util.List;
import java.util.Optional;

/**
 * Supplies the contribution limits used by room checks (F06).
 * Tries each {@link LimitsProvider} in order (ordered fallback) and keeps the
 * first result in memory. The remote provider, validator and on-disk cache
 * from the F15 design are added in a later step; until then the bundled file
 * is the only provider.
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

    /** Asks the providers again, in order, and keeps the first answer. */
    public synchronized void reload() {
        for (LimitsProvider p : providers) {
            Optional<ContributionLimits> limits = p.load();
            if (limits.isPresent()) {
                current = limits.get();
                currentSource = p.name();
                return;
            }
        }
        throw new IllegalStateException("No contribution limits available from any source");
    }

    /** Name of the provider the current limits came from, e.g. "bundled". */
    public synchronized String currentSource() {
        currentLimits();
        return currentSource;
    }
}
