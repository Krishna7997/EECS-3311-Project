package com.maplecfo.automation.limits;

import com.maplecfo.domain.Money;
import com.maplecfo.planning.ContributionLimits;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F15 safety check before new limits replace the current ones. Downloaded data is only trusted when it
 * looks like a normal CRA update: past years unchanged, TFSA limits in $500 steps, no big jumps, and the
 * FHSA rules still as legislated. Anything else is rejected and the app keeps its current limits.
 */
public class LimitsValidator {

    static final Money MAX_TFSA_JUMP = Money.of(1000);
    static final double MAX_RRSP_JUMP = 0.10; // 10% per year

    /** @return the problems found; empty when the new limits can be used */
    public List<String> validate(ContributionLimits next, ContributionLimits previous, int currentYear) {
        List<String> problems = new ArrayList<>();
        // past years must not change
        for (Map.Entry<Integer, Money> e : previous.tfsaAnnualLimits().entrySet()) {
            Money n = next.tfsaAnnualLimits().get(e.getKey());
            if (e.getKey() < currentYear && (n == null || !n.equals(e.getValue()))) {
                problems.add("TFSA " + e.getKey() + " changed from " + e.getValue() + " to " + n);
            }
        }
        for (Map.Entry<Integer, Money> e : previous.rrspDollarLimits().entrySet()) {
            Money n = next.rrspDollarLimits().get(e.getKey());
            if (e.getKey() < currentYear && (n == null || !n.equals(e.getValue()))) {
                problems.add("RRSP " + e.getKey() + " changed from " + e.getValue() + " to " + n);
            }
        }
        // TFSA: $500 steps and at most $1,000 more than the year before (2015 was a one-off)
        Money prior = null;
        for (Map.Entry<Integer, Money> e : next.tfsaAnnualLimits().entrySet()) {
            Money v = e.getValue();
            if (!v.isPositive() || v.getAmount().remainder(BigDecimal.valueOf(500)).signum() != 0) {
                problems.add("TFSA " + e.getKey() + " limit " + v + " is not a multiple of $500");
            }
            if (prior != null && e.getKey() != 2015 && e.getKey() != 2016 && v.minus(prior).abs().isGreaterThan(MAX_TFSA_JUMP)) {
                problems.add("TFSA " + e.getKey() + " jumps by more than " + MAX_TFSA_JUMP);
            }
            prior = v;
        }
        // RRSP: no jump above 10%
        Money rPrior = null;
        for (Map.Entry<Integer, Money> e : next.rrspDollarLimits().entrySet()) {
            if (rPrior != null && e.getValue().isGreaterThan(rPrior.times(1 + MAX_RRSP_JUMP))) {
                problems.add("RRSP " + e.getKey() + " jumps by more than 10%");
            }
            if (!e.getValue().isPositive()) problems.add("RRSP " + e.getKey() + " limit must be positive");
            rPrior = e.getValue();
        }
        if (next.rrspEarnedIncomeRate() != previous.rrspEarnedIncomeRate()) {
            problems.add("RRSP earned-income rate changed");
        }
        // FHSA is set in law: $8,000 a year, $40,000 lifetime
        if (!next.fhsaAnnualLimit().equals(Money.of(8000)) || !next.fhsaLifetimeLimit().equals(Money.of(40000))
                || next.fhsaFirstYear() != 2023) {
            problems.add("FHSA rules differ from $8,000/year, $40,000 lifetime, since 2023");
        }
        if (next.latestTfsaYear() < previous.latestTfsaYear()) {
            problems.add("New data is older than the current data");
        }
        return problems;
    }
}
