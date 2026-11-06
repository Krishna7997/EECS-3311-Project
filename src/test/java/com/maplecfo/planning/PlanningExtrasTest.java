package com.maplecfo.planning;

import com.maplecfo.command.CreateGoalCommand;
import com.maplecfo.command.RecordContributionCommand;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.CsvReader;
import com.maplecfo.importer.DefaultProfiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Smaller rules: goal forecast wording, command descriptions and CSV format detection (F01, F09, F13, F14). */
class PlanningExtrasTest {

    private static SavingsGoal goal(String saved, LocalDate deadline) {
        return new SavingsGoal("g", "Trip", Money.of(1000), Money.of(saved), deadline, null);
    }

    private static CashFlowForecast cf(int months, long surplus) {
        return new CashFlowForecast(months, Money.of(3000), Money.of(3000 - surplus), Money.of(surplus), Money.of(500),
                months < 2);
    }

    @Test
    void goalForecastSummaries() {
        GoalForecast done = new GoalForecast(goal("1000", null), 0, Money.zero(), Money.zero(), null, true,
                Money.zero(), false);
        assertEquals("Trip is complete: $1,000.00 of $1,000.00 saved.", done.summary());

        GoalForecast noDeadline = new GoalForecast(goal("100", null), 0, Money.zero(), Money.of(300),
                LocalDate.of(2027, 2, 1), true, Money.zero(), false);
        assertTrue(noDeadline.summary().contains("No deadline; about $300.00 a month is available."));

        GoalForecast behind = new GoalForecast(goal("0", LocalDate.of(2027, 1, 1)), 2, Money.of(500), Money.of(200),
                null, false, Money.of(300), true);
        String s = behind.summary();
        assertTrue(s.contains("Not on track: short by $300.00 a month."), s);
        assertTrue(s.contains("Low confidence"), s);
    }

    @Test
    void cashFlowForecastHasData() {
        assertFalse(cf(0, 0).hasData());
        assertTrue(cf(1, 200).hasData());
        assertTrue(cf(1, 200).lowConfidence());
        assertFalse(cf(3, 200).lowConfidence());
    }

    @Test
    void affordabilitySummaryForEachVerdict() {
        AffordabilityResult insufficient = new AffordabilityResult(Money.of(100), LocalDate.of(2027, 1, 1),
                AffordabilityResult.Verdict.INSUFFICIENT_DATA, Money.zero(), Money.zero(), Money.zero(), Money.zero(),
                Money.zero(), List.of(), cf(0, 0));
        assertTrue(insufficient.summary().startsWith("Not enough history"));

        AffordabilityResult no = new AffordabilityResult(Money.of(5000), LocalDate.of(2027, 1, 1),
                AffordabilityResult.Verdict.NO, Money.of(1000), Money.of(2000), Money.of(500), Money.of(-3500),
                Money.of(3500), List.of(), cf(3, 300));
        assertTrue(no.summary().startsWith("Not yet: by 2027-01-01 you are projected to have $2,000.00"), no.summary());
        assertThrows(UnsupportedOperationException.class, () -> no.affectedGoals().add("x"), "list is read-only");
    }

    @Test
    void commandDescriptions() {
        assertEquals("Create goal Trip ($1,000.00 by 2027-01-01)",
                new CreateGoalCommand(dummyGoals(), goal("0", LocalDate.of(2027, 1, 1))).describe());
        ContributionRecord r = new ContributionRecord(AccountType.TFSA, LocalDate.of(2026, 6, 25), Money.of(500),
                ContributionKind.CONTRIBUTION);
        RecordContributionCommand c = new RecordContributionCommand(dummyRegistered(), r);
        assertEquals("Record $500.00 TFSA contribution on 2026-06-25", c.describe());
        assertSame(r, c.record());
    }

    private static GoalService dummyGoals() {
        return new GoalService(null, null, null, java.time.Clock.systemUTC());
    }

    private static RegisteredAccountService dummyRegistered() {
        return new RegisteredAccountService(com.maplecfo.automation.limits.LimitsService.bundledOnly(),
                new com.maplecfo.persistence.memory.InMemoryProfileRepository(),
                new com.maplecfo.persistence.memory.InMemoryContributionRepository(), new com.maplecfo.events.EventBus());
    }

    @Test
    void csvFormatDetection() throws Exception {
        List<String> td = CsvReader.read(Path.of("sample-data/td_chequing_sample.csv")).get(0);
        List<String> rbc = CsvReader.read(Path.of("sample-data/rbc_visa_sample.csv")).get(0);
        List<String> generic = CsvReader.read(Path.of("sample-data/savings_generic_sample.csv")).get(0);

        assertTrue(DefaultProfiles.TD_BANK.matchesFirstRow(td));
        assertTrue(DefaultProfiles.RBC.matchesFirstRow(rbc));
        assertTrue(DefaultProfiles.GENERIC.matchesFirstRow(generic));
        assertFalse(DefaultProfiles.RBC.matchesFirstRow(td));
        assertFalse(DefaultProfiles.TD_BANK.matchesFirstRow(generic), "header row is not a date");
        assertFalse(DefaultProfiles.TD_BANK.matchesFirstRow(List.of("x")), "too few columns");
    }

    @Test
    void columnMappingNamesAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new ColumnMapping(" ", true, "Date", "yyyy-MM-dd",
                "Description", "Amount", null, null, false, null));
    }
}
