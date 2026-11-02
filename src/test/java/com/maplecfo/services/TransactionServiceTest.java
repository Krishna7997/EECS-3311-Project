package com.maplecfo.services;

import com.maplecfo.categorization.CategorizationService;
import com.maplecfo.command.CommandHistory;
import com.maplecfo.command.RecategorizeCommand;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Transaction;
import com.maplecfo.events.EventBus;
import com.maplecfo.events.EventType;
import com.maplecfo.events.FinanceEvent;
import com.maplecfo.importer.DefaultProfiles;
import com.maplecfo.importer.ImportResult;
import com.maplecfo.importer.MappingMismatchException;
import com.maplecfo.persistence.memory.InMemoryMappingProfileRepository;
import com.maplecfo.persistence.memory.InMemoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {

    @TempDir
    Path dir;
    private TransactionService service;
    private final List<FinanceEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        EventBus bus = new EventBus();
        bus.subscribe(EventType.TRANSACTIONS_IMPORTED, events::add);
        bus.subscribe(EventType.TRANSACTION_RECATEGORIZED, events::add);
        InMemoryMappingProfileRepository profiles = new InMemoryMappingProfileRepository();
        DefaultProfiles.all().forEach(profiles::save);
        service = new TransactionService(profiles, CategorizationService.rulesOnly(),
                new InMemoryTransactionRepository(), bus);
    }

    private Path td(String content) throws IOException {
        Path p = dir.resolve("td.csv");
        Files.writeString(p, content);
        return p;
    }

    private static final String TD = """
            10/01/2026,LOBLAWS #1032,54.20,,945.80
            10/01/2026,TIM HORTONS #2231,2.10,,943.70
            10/01/2026,TIM HORTONS #2231,2.10,,941.60
            10/02/2026,MYSTERY STORE 88,12.00,,929.60
            """;

    @Test
    void importCategorizesSavesAndPublishesAnEvent() throws IOException {
        ImportResult r = service.importFrom(td(TD), "chq", "TD bank account");
        assertEquals(4, r.imported(), "two identical coffees on one day are both kept");
        assertEquals(0, r.duplicatesSkipped());
        assertEquals(1, r.uncategorized());
        assertEquals(1, service.needsReview().size());
        assertEquals(1, events.size());
        assertEquals(EventType.TRANSACTIONS_IMPORTED, events.get(0).type());
    }

    @Test
    void reimportingTheSameFileSkipsEveryRow() throws IOException {
        Path f = td(TD);
        service.importFrom(f, "chq", "TD bank account");
        ImportResult again = service.importFrom(f, "chq", "TD bank account");
        assertEquals(0, again.imported());
        assertEquals(4, again.duplicatesSkipped());
        assertEquals(4, service.findAll().size());
    }

    @Test
    void overlappingExportOnlyAddsNewRows() throws IOException {
        service.importFrom(td(TD), "chq", "TD bank account");
        ImportResult r = service.importFrom(td(TD + "10/03/2026,NETFLIX.COM,16.99,,912.61\n"), "chq", "TD bank account");
        assertEquals(1, r.imported());
        assertEquals(4, r.duplicatesSkipped());
    }

    @Test
    void unknownProfileAsksForAMapping() {
        assertThrows(MappingMismatchException.class, () -> service.importFrom(td(TD), "chq", "Nope"));
    }

    @Test
    void correctionIsUndoableAndTeachesARule() throws IOException {
        service.importFrom(td(TD), "chq", "TD bank account");
        Transaction mystery = service.search("mystery").get(0);
        CommandHistory history = new CommandHistory();

        history.run(new RecategorizeCommand(service, mystery.getId(), Category.SHOPPING));
        assertEquals(Category.SHOPPING, service.findById(mystery.getId()).orElseThrow().getCategory());
        assertEquals(CategorySource.USER, service.findById(mystery.getId()).orElseThrow().getCategorySource());

        history.undo();
        Transaction back = service.findById(mystery.getId()).orElseThrow();
        assertEquals(Category.UNCATEGORIZED, back.getCategory());
        assertEquals(CategorySource.NONE, back.getCategorySource());

        // the learned rule categorizes the next import from the same merchant
        Path next = dir.resolve("next.csv");
        Files.writeString(next, "10/09/2026,MYSTERY STORE 88,9.00,,900.00\n");
        service.importFrom(next, "chq", "TD bank account");
        assertEquals(Category.SHOPPING, service.search("mystery").get(0).getCategory());
    }

    @Test
    void undoFailsCleanlyWhenTheTransactionIsGone() {
        CommandHistory history = new CommandHistory();
        assertThrows(java.util.NoSuchElementException.class,
                () -> history.run(new RecategorizeCommand(service, "missing-id", Category.DINING)));
        assertFalse(history.canUndo());
    }

    @Test
    void sampleFilesInTheRepoImportCleanly() throws IOException {
        Path root = Path.of("sample-data");
        ImportResult td = service.importFrom(root.resolve("td_chequing_sample.csv"), "chq", "TD bank account");
        ImportResult rbc = service.importFrom(root.resolve("rbc_visa_sample.csv"), "visa", "RBC");
        ImportResult sav = service.importFrom(root.resolve("savings_generic_sample.csv"), "sav",
                "Generic (Date, Description, Amount)");
        assertTrue(td.imported() > 100);
        assertTrue(td.rejectedRows().isEmpty());
        assertEquals(1, rbc.rejectedRows().size(), "the sample has one broken row on purpose");
        assertTrue(sav.latestBalanceIfKnown().isPresent());
    }
}
