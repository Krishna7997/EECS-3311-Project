package com.maplecfo.automation.limits;

import com.maplecfo.planning.ContributionLimits;

import java.util.Optional;

/**
 * Strategy pattern: one source of contribution limits (remote file, local
 * cache, or the copy bundled with the app). {@link LimitsService} tries the
 * providers in order and uses the first one that answers.
 */
public interface LimitsProvider {

    /** The limits, or empty when this source is unavailable or unreadable. */
    Optional<ContributionLimits> load();

    String name();
}
