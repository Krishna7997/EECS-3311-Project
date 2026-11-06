package com.maplecfo.automation.limits;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.ContributionLimits;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F15 weekly bot (run by .github/workflows/limits-update.yml). Reads the CRA page, merges any new
 * TFSA/RRSP years into contribution_limits.json, validates the result, and writes the file only when it
 * passes. The workflow then opens a pull request for a person to review; problems open an issue.
 *
 * <pre>java -cp maplecfo.jar com.maplecfo.automation.limits.LimitsUpdateJob src/main/resources/contribution_limits.json [--html saved-page.html]</pre>
 *
 * Exit codes: 0 = no change, 10 = file updated, 2 = rejected or failed.
 */
public final class LimitsUpdateJob {

    public static final int NO_CHANGE = 0;
    public static final int UPDATED = 10;
    public static final int FAILED = 2;

    public record Result(int exitCode, String message, List<String> changes) {
    }

    private final CraLimitsScraper scraper = new CraLimitsScraper();
    private final LimitsValidator validator = new LimitsValidator();
    private final Clock clock;

    public LimitsUpdateJob(Clock clock) {
        this.clock = clock;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: LimitsUpdateJob <contribution_limits.json> [--html saved-page.html]");
            System.exit(FAILED);
        }
        Path json = Path.of(args[0]);
        String html = args.length >= 3 && args[1].equals("--html")
                ? Files.readString(Path.of(args[2]))
                : RemoteLimitsProvider.overHttp(URI.create(CraLimitsScraper.CRA_URL)).fetch();
        Result r = new LimitsUpdateJob(Clock.systemDefaultZone()).run(json, html);
        List<LimitsNotifier> notifiers = new ArrayList<>();
        notifiers.add(new ConsoleNotifier());
        GitHubIssueNotifier gh = GitHubIssueNotifier.fromEnvironment();
        if (gh != null && r.exitCode() == FAILED) {
            notifiers.add(gh); // a successful update becomes a pull request instead (see the workflow)
        }
        String title = r.exitCode() == UPDATED ? "Contribution limits update ready for review"
                : r.exitCode() == FAILED ? "Contribution limits check needs attention" : "Contribution limits unchanged";
        for (LimitsNotifier n : notifiers) {
            n.notify(title, r.message() + (r.changes().isEmpty() ? "" : "\n- " + String.join("\n- ", r.changes())));
        }
        System.exit(r.exitCode());
    }

    /** Merges the scraped values into the JSON file; writes it only if the result is valid and different. */
    public Result run(Path jsonFile, String craHtml) throws IOException {
        String original = Files.readString(jsonFile);
        ContributionLimits current = parse(original);
        CraLimitsScraper.Scraped s = scraper.parse(craHtml);
        if (s.isEmpty()) {
            return new Result(FAILED, "Could not find the TFSA and RRSP columns on the CRA page; it may have changed.",
                    List.of());
        }
        int thisYear = LocalDate.now(clock).getYear();
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode root = (ObjectNode) mapper.readTree(original);
        List<String> changes = new ArrayList<>();
        merge((ObjectNode) root.get("tfsa"), s.tfsa(), current.tfsaAnnualLimits(), thisYear, "TFSA", changes);
        merge((ObjectNode) root.get("rrsp").get("dollarLimit"), s.rrsp(), current.rrspDollarLimits(), thisYear,
                "RRSP", changes);
        if (changes.isEmpty()) {
            return new Result(NO_CHANGE, "contribution_limits.json already matches the CRA page.", changes);
        }
        root.put("updatedAt", LocalDate.now(clock).toString());
        String updated = mapper.writeValueAsString(root) + "\n";
        List<String> problems = validator.validate(parse(updated), current, thisYear);
        if (!problems.isEmpty()) {
            return new Result(FAILED, "The CRA values did not pass validation, so nothing was changed: "
                    + String.join("; ", problems), changes);
        }
        Files.writeString(jsonFile, updated, StandardCharsets.UTF_8);
        return new Result(UPDATED, "contribution_limits.json was updated from the CRA page. Please review.", changes);
    }

    /** Only the current and future years are taken from the page; past years stay as they are. */
    private static void merge(ObjectNode node, Map<Integer, Money> scraped, Map<Integer, Money> current, int thisYear,
                              String label, List<String> changes) {
        for (Map.Entry<Integer, Money> e : scraped.entrySet()) {
            if (e.getKey() < thisYear) continue;
            Money old = current.get(e.getKey());
            if (old == null || !old.equals(e.getValue())) {
                node.put(String.valueOf(e.getKey()), new java.math.BigDecimal(e.getValue().getAmount().stripTrailingZeros().toPlainString()));
                changes.add(label + " " + e.getKey() + ": " + (old == null ? "new" : old.toString()) + " → " + e.getValue());
            }
        }
    }

    private static ContributionLimits parse(String json) throws IOException {
        return ContributionLimits.loadFrom(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
