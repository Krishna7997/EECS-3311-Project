package com.maplecfo.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maplecfo.domain.Money;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * CRA contribution limits, loaded from {@code contribution_limits.json}
 * (Stage 1 decision D8: limits are data, not code). The file is replaced
 * automatically by the limits bot (F15), so no code change is needed each year.
 */
public final class ContributionLimits {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TreeMap<Integer, Money> tfsaAnnualLimits;
    private final Map<Integer, Money> rrspDollarLimits;
    private final double rrspEarnedIncomeRate;
    private final int fhsaFirstYear;
    private final Money fhsaAnnualLimit;
    private final Money fhsaLifetimeLimit;
    private final Money fhsaCarryForwardMax;

    public ContributionLimits(Map<Integer, Money> tfsaAnnualLimits, Map<Integer, Money> rrspDollarLimits,
                              double rrspEarnedIncomeRate, int fhsaFirstYear, Money fhsaAnnualLimit,
                              Money fhsaLifetimeLimit, Money fhsaCarryForwardMax) {
        this.tfsaAnnualLimits = new TreeMap<>(tfsaAnnualLimits);
        this.rrspDollarLimits = Collections.unmodifiableMap(new TreeMap<>(rrspDollarLimits));
        this.rrspEarnedIncomeRate = rrspEarnedIncomeRate;
        this.fhsaFirstYear = fhsaFirstYear;
        this.fhsaAnnualLimit = Objects.requireNonNull(fhsaAnnualLimit);
        this.fhsaLifetimeLimit = Objects.requireNonNull(fhsaLifetimeLimit);
        this.fhsaCarryForwardMax = Objects.requireNonNull(fhsaCarryForwardMax);
        if (this.tfsaAnnualLimits.isEmpty()) {
            throw new IllegalArgumentException("TFSA limits are missing");
        }
    }

    public static ContributionLimits loadFrom(Path json) throws IOException {
        try (InputStream in = Files.newInputStream(json)) {
            return loadFrom(in);
        }
    }

    public static ContributionLimits loadFrom(InputStream json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        if (root == null || !root.has("tfsa") || !root.has("rrsp") || !root.has("fhsa")) {
            throw new IOException("contribution_limits.json must contain tfsa, rrsp and fhsa sections");
        }
        JsonNode rrsp = root.get("rrsp");
        JsonNode fhsa = root.get("fhsa");
        return new ContributionLimits(
                yearMap(root.get("tfsa")),
                yearMap(rrsp.path("dollarLimit")),
                requireNumber(rrsp, "earnedIncomeRate").asDouble(),
                requireNumber(fhsa, "firstYear").asInt(),
                Money.of(requireNumber(fhsa, "annualLimit").decimalValue()),
                Money.of(requireNumber(fhsa, "lifetimeLimit").decimalValue()),
                Money.of(requireNumber(fhsa, "carryForwardMax").decimalValue()));
    }

    private static JsonNode requireNumber(JsonNode parent, String field) throws IOException {
        JsonNode n = parent.get(field);
        if (n == null || !n.isNumber()) {
            throw new IOException("Missing or non-numeric field: " + field);
        }
        return n;
    }

    private static Map<Integer, Money> yearMap(JsonNode node) throws IOException {
        Map<Integer, Money> map = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!e.getValue().isNumber()) {
                throw new IOException("Limit for " + e.getKey() + " is not a number");
            }
            try {
                map.put(Integer.parseInt(e.getKey()), Money.of(e.getValue().decimalValue()));
            } catch (NumberFormatException ex) {
                throw new IOException("Not a year: " + e.getKey(), ex);
            }
        }
        return map;
    }

    /** The TFSA dollar limit for one year. */
    public Money tfsaLimitFor(int year) {
        Money limit = tfsaAnnualLimits.get(year);
        if (limit == null) {
            throw new IllegalArgumentException("No TFSA limit known for " + year);
        }
        return limit;
    }

    public boolean hasTfsaLimitFor(int year) {
        return tfsaAnnualLimits.containsKey(year);
    }

    public int firstTfsaYear() {
        return tfsaAnnualLimits.firstKey();
    }

    public int latestTfsaYear() {
        return tfsaAnnualLimits.lastKey();
    }

    public Map<Integer, Money> tfsaAnnualLimits() {
        return Collections.unmodifiableMap(tfsaAnnualLimits);
    }

    public Map<Integer, Money> rrspDollarLimits() {
        return rrspDollarLimits;
    }

    public double rrspEarnedIncomeRate() {
        return rrspEarnedIncomeRate;
    }

    public int fhsaFirstYear() {
        return fhsaFirstYear;
    }

    public Money fhsaAnnualLimit() {
        return fhsaAnnualLimit;
    }

    public Money fhsaLifetimeLimit() {
        return fhsaLifetimeLimit;
    }

    public Money fhsaCarryForwardMax() {
        return fhsaCarryForwardMax;
    }
}
