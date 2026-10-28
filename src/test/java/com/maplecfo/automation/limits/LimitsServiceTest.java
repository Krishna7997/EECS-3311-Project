package com.maplecfo.automation.limits;

import com.maplecfo.domain.Money;
import com.maplecfo.planning.ContributionLimits;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LimitsServiceTest {

    @Test
    void bundledFileHasCurrentCraLimits() {
        ContributionLimits limits = LimitsService.bundledOnly().currentLimits();
        assertEquals(Money.of(7_000), limits.tfsaLimitFor(2026));
        assertEquals(Money.of(10_000), limits.tfsaLimitFor(2015));
        assertEquals(2009, limits.firstTfsaYear());
        assertEquals(Money.of(8_000), limits.fhsaAnnualLimit());
        assertEquals(Money.of(40_000), limits.fhsaLifetimeLimit());
        assertEquals(Money.of(33_810), limits.rrspDollarLimits().get(2026));
    }

    @Test
    void fallsBackToTheNextProviderWhenOneFails() {
        LimitsProvider broken = new LimitsProvider() {
            public Optional<ContributionLimits> load() { return Optional.empty(); }
            public String name() { return "remote"; }
        };
        LimitsService service = new LimitsService(List.of(broken, new BundledLimitsProvider()));
        assertNotNull(service.currentLimits());
        assertEquals("bundled", service.currentSource());
    }

    @Test
    void failsClearlyWhenNoSourceWorks() {
        LimitsService service = new LimitsService(List.of(new BundledLimitsProvider("/does-not-exist.json")));
        assertThrows(IllegalStateException.class, service::currentLimits);
    }

    @Test
    void malformedJsonIsRejected() {
        String bad = "{\"tfsa\": {\"2026\": \"seven thousand\"}, \"rrsp\": {}, \"fhsa\": {}}";
        assertThrows(IOException.class, () ->
                ContributionLimits.loadFrom(new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8))));
    }
}
