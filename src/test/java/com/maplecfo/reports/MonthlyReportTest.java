package com.maplecfo.reports;

import com.maplecfo.agent.llm.LlmException;
import com.maplecfo.agent.llm.MockLlmClient;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.testutil.FakeLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MonthlyReportTest {

    private static final Clock NOV = Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Toronto"));
    private static final YearMonth OCT = YearMonth.of(2026, 10);

    @TempDir
    Path dir;

    /** Note: the first fake reply is used by the AI categorizer during import (one unknown merchant). */
    private MapleCfoFacade app(com.maplecfo.agent.llm.LlmClient llm) throws Exception {
        MapleCfoFacade f = AppContext.create(DatabaseManager.forFile(dir.resolve("r.db")), NOV, llm);
        f.importTransactions(Path.of("sample-data/td_chequing_sample.csv"), "chq", "TD bank account");
        return f;
    }

    @Test
    void figuresAreComputedByCodeNotByTheAi() throws Exception {
        MonthlyReport r = app(new MockLlmClient(List.of())).generateMonthlyReport(OCT);
        assertTrue(r.hasData());
        assertEquals(Money.of("1890.00"), r.income());
        assertEquals(Money.of(1450), r.spendingByCategory().get(Category.RENT));
        assertEquals(r.income().minus(r.spending()), r.saved());
        assertFalse(r.narrativeByAi(), "demo mode uses the template");
        assertTrue(r.narrative().startsWith("In 2026-10 you earned $1,890.00"));
    }

    @Test
    void aiNarrativeIsUsedWhenItOnlyUsesTheFigures() throws Exception {
        FakeLlm llm = new FakeLlm("OTHER", "October went fine: you earned $1,890.00 and rent was your biggest cost.");
        MonthlyReport r = app(llm).generateMonthlyReport(OCT);
        assertTrue(r.narrativeByAi());
        String sent = llm.received.get(llm.received.size() - 1).get(1).content();
        assertTrue(sent.contains("\"income\":\"$1,890.00\""), "only computed figures are sent");
        assertFalse(sent.contains("NETFLIX"), "no raw transactions are sent to the AI");
    }

    @Test
    void aiNarrativeWithAnInventedAmountFallsBackToTheTemplate() throws Exception {
        MonthlyReport r = app(new FakeLlm("OTHER", "You saved $9,999.00 this month!")).generateMonthlyReport(OCT);
        assertFalse(r.narrativeByAi());
    }

    @Test
    void modelOutageFallsBackToTheTemplate() throws Exception {
        MonthlyReport r = app(new FakeLlm("OTHER", new LlmException("busy", true))).generateMonthlyReport(OCT);
        assertFalse(r.narrativeByAi());
        assertFalse(r.narrative().isBlank());
    }

    @Test
    void emptyMonthGivesAMessage() throws Exception {
        MonthlyReport r = app(new MockLlmClient(List.of())).generateMonthlyReport(YearMonth.of(2025, 1));
        assertFalse(r.hasData());
        assertTrue(r.narrative().contains("no transactions"));
    }
}
