package com.maplecfo.automation.limits;

import com.maplecfo.planning.ContributionLimits;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** The last-resort source: {@code contribution_limits.json} packaged inside the app. */
public class BundledLimitsProvider implements LimitsProvider {

    public static final String DEFAULT_RESOURCE = "/contribution_limits.json";

    private final String resourcePath;

    public BundledLimitsProvider() {
        this(DEFAULT_RESOURCE);
    }

    public BundledLimitsProvider(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    @Override
    public Optional<ContributionLimits> load() {
        try (InputStream in = BundledLimitsProvider.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(ContributionLimits.loadFrom(in));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public String name() {
        return "bundled";
    }
}
