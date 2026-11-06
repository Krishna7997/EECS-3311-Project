package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Transaction;
import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.ImportResult;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.YearMonth;
import java.util.List;

/** F01 import and F02 categorisation: import a bank CSV, browse a month, fix categories. */
final class TransactionsView implements View {

    private final MapleCfoFacade app;
    private final VBox root;
    private final TableView<Transaction> table = Ui.table("No transactions. Import a CSV export from your bank.");
    private final TextField accountId = Ui.field("Account id, e.g. chequing", 160);
    private final ComboBox<String> profile = new ComboBox<>();
    private final Label monthLabel = Ui.heading("");
    private final TextField search = Ui.field("Search description", 200);
    private final CheckBox reviewOnly = new CheckBox("Only ones needing review");
    private final ComboBox<Category> newCategory = new ComboBox<>();
    private final Label status = Ui.muted("");
    private YearMonth month;

    TransactionsView(MapleCfoFacade app) {
        this.app = app;
        this.month = YearMonth.from(app.today());

        Button importBtn = new Button("Import CSV…");
        importBtn.setOnAction(e -> chooseAndImport(importBtn.getScene().getWindow()));
        accountId.setText("chequing");

        Button prev = new Button("◀");
        prev.setOnAction(e -> { month = month.minusMonths(1); refresh(); });
        Button next = new Button("▶");
        next.setOnAction(e -> { month = month.plusMonths(1); refresh(); });
        search.setOnAction(e -> refresh());
        reviewOnly.setOnAction(e -> refresh());

        table.getColumns().add(Ui.column("Date", Transaction::getDate, 100));
        table.getColumns().add(Ui.column("Description", Transaction::getDescription, 320));
        table.getColumns().add(Ui.column("Amount", Transaction::getAmount, 110));
        table.getColumns().add(Ui.column("Category", Transaction::getCategory, 130));
        table.getColumns().add(Ui.column("Set by", t -> t.getCategorySource().name().toLowerCase(), 90));
        table.getColumns().add(Ui.column("Account", Transaction::getAccountId, 100));
        VBox.setVgrow(table, Priority.ALWAYS);

        newCategory.getItems().setAll(List.of(Category.values()).stream()
                .filter(c -> c != Category.UNCATEGORIZED).toList());
        newCategory.setPromptText("New category");
        Button apply = new Button("Change category");
        apply.setOnAction(e -> Ui.attempt(this::recategorize));

        root = Ui.page(
                Ui.row(new Label("Import into"), accountId, new Label("Bank format"), profile, importBtn),
                status,
                Ui.row(prev, monthLabel, next, search, reviewOnly),
                table,
                Ui.row(new Label("Selected transaction:"), newCategory, apply,
                        Ui.muted("MapleCFO remembers the merchant for next time. Use Undo to revert.")));
    }

    @Override public String title() { return "Transactions"; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        String selected = profile.getValue();
        profile.getItems().setAll(app.getMappingProfiles().stream().map(ColumnMapping::profileName).toList());
        profile.setValue(selected != null ? selected : profile.getItems().isEmpty() ? null : profile.getItems().get(0));

        List<Transaction> rows;
        String q = search.getText() == null ? "" : search.getText().trim();
        if (reviewOnly.isSelected()) {
            rows = app.getTransactionsNeedingReview();
            monthLabel.setText("Needs review");
        } else if (!q.isEmpty()) {
            rows = app.searchTransactions(q);
            monthLabel.setText("Search: " + q);
        } else {
            rows = app.getTransactions(month);
            monthLabel.setText(month.toString());
        }
        table.getItems().setAll(rows);
    }

    private void chooseAndImport(Window owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a bank CSV export");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showOpenDialog(owner);
        if (f != null) {
            Ui.attempt(() -> importFile(f));
        }
    }

    /** Package-private so a smoke test can import without the file dialog. */
    void importFile(File f) {
        if (accountId.getText().isBlank()) throw new IllegalArgumentException("Enter the account id to import into");
        try {
            ImportResult r = app.importTransactions(f.toPath(), accountId.getText().trim(), profile.getValue());
            status.setText(r.toString());
            if (!r.rejectedRows().isEmpty()) {
                Ui.info("Imported " + r.imported() + " rows. " + r.rejectedRows().size()
                        + " rows could not be read:\n" + String.join("\n", r.rejectedRows()));
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read the file: " + e.getMessage());
        }
        refresh();
    }

    private void recategorize() {
        Transaction t = table.getSelectionModel().getSelectedItem();
        if (t == null) throw new IllegalArgumentException("Select a transaction first");
        if (newCategory.getValue() == null) throw new IllegalArgumentException("Choose the new category");
        app.recategorize(t.getId(), newCategory.getValue());
        status.setText("Changed " + t.getDescription() + " to " + newCategory.getValue() + ".");
        refresh();
    }
}
