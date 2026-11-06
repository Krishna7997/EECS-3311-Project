package com.maplecfo.importer;

import com.maplecfo.agent.PromptBuilder;
import com.maplecfo.agent.llm.MockLlmClient;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Money;
import com.maplecfo.persistence.sqlite.DatabaseManager;
import com.maplecfo.testutil.FakeLlm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** F16: the AI proposes how to read an unknown bank CSV; Java checks it against the file before anything is saved. */
class AiFormatReaderTest {

    private static final Path UNKNOWN = Path.of("sample-data/demo/errors/unknown_bank_format.csv");
    private static final String GOOD = """
            Here is the format:
            ```json
            {"hasHeader": true, "dateColumn": "Transaction date", "dateFormat": "MM/dd/yyyy",
             "descriptionColumn": "Name", "amountColumn": "Amount", "debitColumn": null, "creditColumn": null,
             "negateAmounts": false, "balanceColumn": null,
             "explanation": "Signed amounts in one column; the shop is in Name."}
            ```""";

    @TempDir
    Path dir;

    private MapleCfoFacade app(Object... replies) {
        return AppContext.create(DatabaseManager.forFile(dir.resolve("a.db")),
                Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneId.of("America/Toronto")), new FakeLlm(replies));
    }

    @Test
    void proposalIsCheckedAgainstTheWholeFileAndSavedOnlyWhenAccepted() throws Exception {
        MapleCfoFacade app = app(GOOD);
        assertTrue(app.detectImportFormat(UNKNOWN).isEmpty());

        AiFormatReader.Proposal p = app.proposeImportFormat(UNKNOWN, "Tangerine");
        assertEquals("Tangerine", p.mapping().profileName());
        assertEquals(3, p.rowsRead());
        assertTrue(p.rowsRejected().isEmpty());
        assertTrue(p.looksRight());
        assertEquals("SOBEYS #771", p.preview().get(0).getDescription());
        assertEquals(Money.of("-64.20"), p.preview().get(0).getAmount());
        assertTrue(p.describeColumns().contains("description from 'Name'"));
        assertTrue(app.detectImportFormat(UNKNOWN).isEmpty(), "nothing is saved before the user accepts");

        assertEquals(3, app.saveFormatAndImport(p, UNKNOWN, "chequing").imported());
        assertEquals(Optional.of("Tangerine"), app.detectImportFormat(UNKNOWN), "detected automatically next time");
    }

    @Test
    void nameIsMadeUniqueAndCanBeChanged() throws Exception {
        MapleCfoFacade app = app(GOOD);
        AiFormatReader.Proposal p = app.proposeImportFormat(UNKNOWN, "RBC");
        assertEquals("RBC (2)", p.mapping().profileName(), "RBC is a built-in format");
        assertEquals("My bank", p.named("My bank").mapping().profileName());
    }

    @Test
    void headerlessFileUsesColumnNumbers() throws Exception {
        Path f = dir.resolve("credit_union.csv");
        Files.writeString(f, "2026-11-02;SOBEYS #771;64.20;0\n2026-11-03;PAYROLL;0;2150.00\n".replace(';', ','));
        MapleCfoFacade app = app("""
                {"hasHeader": false, "dateColumn": "1", "dateFormat": "yyyy-MM-dd", "descriptionColumn": "2",
                 "amountColumn": null, "debitColumn": "3", "creditColumn": "4", "negateAmounts": false,
                 "balanceColumn": null, "explanation": "No header; money out and in are separate."}""");
        AiFormatReader.Proposal p = app.proposeImportFormat(f, null);
        assertEquals("From credit_union.csv", p.mapping().profileName());
        assertEquals(2, p.rowsRead());
        assertEquals(Money.of("-64.20"), p.preview().get(0).getAmount());
        assertEquals(Money.of("2150.00"), p.preview().get(1).getAmount());
    }

    @Test
    void wrongColumnsOrUnreadableAnswersAreRejected() {
        String wrongColumn = GOOD.replace("\"Name\"", "\"Merchant\"");
        assertThrows(IllegalArgumentException.class, () -> app(wrongColumn).proposeImportFormat(UNKNOWN, "X"));
        assertThrows(IllegalArgumentException.class, () -> app("Sorry, I can't tell.").proposeImportFormat(UNKNOWN, "X"));
        String noDate = GOOD.replace("\"dateFormat\": \"MM/dd/yyyy\",", "");
        assertThrows(IllegalArgumentException.class, () -> app(noDate).proposeImportFormat(UNKNOWN, "X"));
    }

    @Test
    void wrongDateFormatShowsUpInTheTestRead() throws Exception {
        AiFormatReader.Proposal p = app(GOOD.replace("MM/dd/yyyy", "yyyy-MM-dd")).proposeImportFormat(UNKNOWN, "X");
        assertEquals(0, p.rowsRead());
        assertEquals(3, p.rowsRejected().size());
        assertFalse(p.looksRight());
    }

    @Test
    void demoModeExplainsThatTheAiIsNeeded() {
        AiFormatReader reader = new AiFormatReader(new MockLlmClient(List.of()), new PromptBuilder());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> reader.propose(UNKNOWN, "X"));
        assertTrue(e.getMessage().contains("API key"));
    }
}
