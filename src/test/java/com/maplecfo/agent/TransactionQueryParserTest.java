package com.maplecfo.agent;

import com.maplecfo.agent.llm.MockLlmClient;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.services.TransactionFilter;
import com.maplecfo.testutil.FakeLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** F18: a plain-English question becomes a checked transaction filter. */
class TransactionQueryParserTest {

    private static final LocalDate NOV6 = LocalDate.of(2026, 11, 6);
    private static final List<String> ACCOUNTS = List.of("chequing", "visa", "savings");

    @TempDir
    Path dir;

    @Test
    void aiAnswerBecomesAFilterAndFindsTheRightRows() throws Exception {
        FakeLlm llm = new FakeLlm();
        MapleCfoFacade app = AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")),
                Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneId.of("America/Toronto")), llm);
        app.importTransactionsAuto(Path.of("sample-data/demo/td_chequing_2025-2026.csv"), "chequing");
        llm.then("""
                {"from": "2026-10-01", "to": "2026-10-31", "account": null, "category": null, "direction": "OUT",
                 "min": 30, "max": null, "text": "uber eats"}""");
        llm.received.clear();

        TransactionQueryParser.ParsedQuery q = app.understandTransactionQuery("Uber Eats over $30 in October");
        assertTrue(q.byAi());
        assertEquals("Showing \"uber eats\", money out, $30.00 or more, 2026-10-01 to 2026-10-31", q.explanation());
        List<Transaction> rows = app.findTransactions(q.filter());
        assertFalse(rows.isEmpty());
        for (Transaction t : rows) {
            assertTrue(t.getDescription().startsWith("UBER EATS"));
            assertEquals(10, t.getDate().getMonthValue());
            assertFalse(t.getAmount().abs().isLessThan(Money.of(30)));
        }
        String prompt = llm.received.get(0).get(0).content();
        assertTrue(prompt.contains("Today is 2026-11-06") && prompt.contains("chequing"), "the AI gets today and the account ids");
    }

    @Test
    void invalidFieldsAreLeftOutAndReported() {
        TransactionQueryParser.ParsedQuery q = TransactionQueryParser.fromJson("""
                {"from": "2026-02-30", "to": "2026-03-31", "account": "amex", "category": "COFFEE",
                 "direction": "SIDEWAYS", "min": -5, "max": 20, "text": null}""", ACCOUNTS);
        TransactionFilter f = q.filter();
        assertNull(f.from());
        assertEquals(LocalDate.of(2026, 3, 31), f.to());
        assertNull(f.accountId());
        assertNull(f.category());
        assertNull(f.direction());
        assertNull(f.minAmount());
        assertEquals(Money.of(20), f.maxAmount());
        assertEquals(5, q.ignored().size(), q.ignored().toString());
        assertTrue(q.explanation().contains("ignored: date 2026-02-30 is not a real date"));
    }

    @Test
    void reversedDatesAndAmountsAreDropped() {
        TransactionFilter f = TransactionQueryParser.fromJson("""
                {"from": "2026-10-31", "to": "2026-10-01", "min": 50, "max": 10, "account": "VISA"}""", ACCOUNTS).filter();
        assertNull(f.from());
        assertNull(f.minAmount());
        assertEquals("VISA", f.accountId(), "account names are matched ignoring case");
    }

    @Test
    void unreadableAnswerIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> TransactionQueryParser.fromJson("I think you mean coffee.", ACCOUNTS));
    }

    @Test
    void demoModeUsesKeywordRules() {
        TransactionQueryParser p = new TransactionQueryParser(new MockLlmClient(List.of()), new PromptBuilder());
        TransactionQueryParser.ParsedQuery q = p.parse("dining over $25 on my visa in October", ACCOUNTS, NOV6);
        assertFalse(q.byAi());
        TransactionFilter f = q.filter();
        assertEquals(Category.DINING, f.category());
        assertEquals("visa", f.accountId());
        assertEquals(Money.of(25), f.minAmount());
        assertEquals(LocalDate.of(2026, 10, 1), f.from());
        assertEquals(LocalDate.of(2026, 10, 31), f.to());

        TransactionFilter dec = p.parse("deposits under 100 in December", ACCOUNTS, NOV6).filter();
        assertEquals(LocalDate.of(2025, 12, 1), dec.from(), "a month later than today means last year");
        assertEquals(TransactionFilter.Direction.IN, dec.direction());
        assertEquals(Money.of(100), dec.maxAmount());
        assertEquals("\"netflix\"", p.parse("find \"netflix\" last month", ACCOUNTS, NOV6).filter().describe().split(",")[0]);
        assertThrows(IllegalArgumentException.class, () -> p.parse(" ", ACCOUNTS, NOV6));
    }
}
