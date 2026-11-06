package com.maplecfo.automation.limits;

import com.maplecfo.domain.Money;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.planning.ContributionLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** F15: validator, cache, refresh, CRA scraper and the weekly bot job. */
class LimitsRefreshTest {

    @TempDir
    Path dir;
    private String bundledJson;
    private final Clock nov2026 = Clock.fixed(Instant.parse("2026-11-01T15:00:00Z"), ZoneId.of("America/Toronto"));

    @BeforeEach
    void setUp() throws IOException {
        bundledJson = new String(LimitsRefreshTest.class.getResourceAsStream("/contribution_limits.json").readAllBytes(),
                StandardCharsets.UTF_8);
    }

    private static ContributionLimits parse(String json) throws IOException {
        return ContributionLimits.loadFrom(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    /** The bundled file plus a 2027 TFSA limit (and optionally other edits). */
    private String with2027(String tfsa2027) {
        return bundledJson.replace("\"2026\": 7000", "\"2026\": 7000, \"2027\": " + tfsa2027)
                .replace("\"2026\": 33810", "\"2026\": 33810, \"2027\": 34950");
    }

    // ------------------------------------------------------------------ validator

    @Test
    void validatorAcceptsNormalUpdatesAndRejectsOddOnes() throws IOException {
        LimitsValidator v = new LimitsValidator();
        ContributionLimits bundled = parse(bundledJson);
        assertTrue(v.validate(bundled, bundled, 2026).isEmpty(), "the bundled file is valid");
        assertTrue(v.validate(parse(with2027("7500")), bundled, 2026).isEmpty());

        assertFalse(v.validate(parse(with2027("7250")), bundled, 2026).isEmpty(), "not a $500 step");
        assertFalse(v.validate(parse(with2027("9000")), bundled, 2026).isEmpty(), "jump over $1,000");
        assertFalse(v.validate(parse(bundledJson.replace("\"2024\": 7000", "\"2024\": 7500")), bundled, 2026)
                .isEmpty(), "past year changed");
        assertFalse(v.validate(parse(bundledJson.replace("\"lifetimeLimit\": 40000", "\"lifetimeLimit\": 50000")),
                bundled, 2026).isEmpty(), "FHSA rules are fixed");
    }

    // ------------------------------------------------------------------ refresh

    private LimitsService service(LimitsCache cache, RemoteLimitsProvider.Fetcher fetcher, EventBus bus) {
        LimitsService s = new LimitsService(List.of(cache, new BundledLimitsProvider()));
        s.configureRefresh(new RemoteLimitsProvider(URI.create("https://example.test/limits.json"), fetcher),
                cache, bus, nov2026);
        return s;
    }

    @Test
    void refreshUpdatesCachesAndAnnounces() throws IOException {
        LimitsCache cache = new LimitsCache(dir.resolve("limits_cache.json"));
        EventBus bus = new EventBus();
        List<Object> events = new ArrayList<>();
        bus.subscribe(EventType.LIMITS_UPDATED, e -> events.add(e.payload()));
        LimitsService s = service(cache, uri -> with2027("7500"), bus);

        assertEquals("bundled", s.currentSource());
        LimitsStatus st = s.refreshIfStale(Duration.ofDays(7));
        assertEquals(LimitsStatus.State.UPDATED, st.state(), st.message());
        assertEquals(Money.of(7500), s.currentLimits().tfsaLimitFor(2027));
        assertEquals(1, events.size());
        assertTrue(Files.exists(dir.resolve("limits_cache.json")));

        // the next start-up reads the cache, so the newer limits survive a restart (even offline)
        LimitsService restarted = service(cache, uri -> { throw new IOException("offline"); }, bus);
        assertEquals("cache", restarted.currentSource());
        assertEquals(Money.of(7500), restarted.currentLimits().tfsaLimitFor(2027));
    }

    @Test
    void recentCheckIsNotRepeated() throws IOException {
        LimitsCache cache = new LimitsCache(dir.resolve("limits_cache.json"));
        int[] calls = {0};
        LimitsService s = new LimitsService(List.of(cache, new BundledLimitsProvider()));
        s.configureRefresh(new RemoteLimitsProvider(URI.create("https://example.test"), uri -> {
            calls[0]++;
            return bundledJson;
        }), cache, null, Clock.systemUTC());
        assertEquals(LimitsStatus.State.UP_TO_DATE, s.refreshIfStale(Duration.ofDays(7)).state());
        assertEquals(LimitsStatus.State.UP_TO_DATE, s.refreshIfStale(Duration.ofDays(7)).state());
        assertEquals(1, calls[0], "second call within a week does not download again");
    }

    @Test
    void failuresKeepTheCurrentLimits() {
        LimitsCache cache = new LimitsCache(dir.resolve("limits_cache.json"));
        LimitsStatus offline = service(cache, uri -> { throw new IOException("no network"); }, null)
                .refreshIfStale(Duration.ZERO);
        assertEquals(LimitsStatus.State.USING_CACHED, offline.state());
        assertTrue(offline.message().contains("no network"));

        LimitsService s = service(cache, uri -> with2027("9000"), null);
        LimitsStatus rejected = s.refreshIfStale(Duration.ZERO);
        assertEquals(LimitsStatus.State.USING_CACHED, rejected.state());
        assertTrue(rejected.message().contains("rejected"), rejected.message());
        assertFalse(s.currentLimits().hasTfsaLimitFor(2027));

        LimitsStatus garbage = service(cache, uri -> "<html>not json</html>", null).refreshIfStale(Duration.ZERO);
        assertEquals(LimitsStatus.State.USING_CACHED, garbage.state());
    }

    @Test
    void anOlderCacheDoesNotHideNewerBundledLimits() throws IOException {
        LimitsCache cache = new LimitsCache(dir.resolve("limits_cache.json"));
        cache.save(bundledJson.replace(", \"2026\": 7000", "").replace(", \"2026\": 33810", ""));
        LimitsService s = new LimitsService(List.of(cache, new BundledLimitsProvider()));
        assertEquals("bundled", s.currentSource());
    }

    // ------------------------------------------------------------------ bot

    private String craPage() throws IOException {
        return new String(LimitsRefreshTest.class.getResourceAsStream("/cra_limits_page.html").readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    void scraperReadsColumnsByHeading() throws IOException {
        CraLimitsScraper.Scraped s = new CraLimitsScraper().parse(craPage());
        assertEquals(Money.of(7500), s.tfsa().get(2027));
        assertEquals(Money.of(34950), s.rrsp().get(2027), "footnote marker is ignored");
        assertEquals(Money.of(33810), s.rrsp().get(2026));
        assertEquals(3, s.tfsa().size(), "the notes row is skipped");
        assertTrue(new CraLimitsScraper().parse("<p>page moved</p>").isEmpty());
    }

    @Test
    void jobUpdatesTheFileOnlyWhenValid() throws IOException {
        Path json = dir.resolve("contribution_limits.json");
        Files.writeString(json, bundledJson);
        LimitsUpdateJob job = new LimitsUpdateJob(nov2026);

        LimitsUpdateJob.Result r = job.run(json, craPage());
        assertEquals(LimitsUpdateJob.UPDATED, r.exitCode(), r.message());
        assertEquals(List.of("TFSA 2027: new → $7,500.00", "RRSP 2027: new → $34,950.00"), r.changes());
        ContributionLimits updated = ContributionLimits.loadFrom(json);
        assertEquals(Money.of(7500), updated.tfsaLimitFor(2027));
        assertEquals(Money.of(7000), updated.tfsaLimitFor(2024), "past years kept");

        assertEquals(LimitsUpdateJob.NO_CHANGE, job.run(json, craPage()).exitCode(), "second run: nothing new");

        Files.writeString(json, bundledJson);
        LimitsUpdateJob.Result bad = job.run(json, craPage().replace("$7,500", "$9,999"));
        assertEquals(LimitsUpdateJob.FAILED, bad.exitCode());
        assertEquals(bundledJson, Files.readString(json), "file untouched when validation fails");

        assertEquals(LimitsUpdateJob.FAILED, job.run(json, "<html></html>").exitCode());
    }

    @Test
    void issueBodyIsValidJson() throws IOException {
        String body = GitHubIssueNotifier.body("Title \"x\"", "line1\nline2");
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        assertEquals("Title \"x\"", node.get("title").asText());
        assertEquals("limits-bot", node.get("labels").get(0).asText());
    }
}
