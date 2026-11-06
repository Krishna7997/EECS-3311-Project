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
    static final String AUTO = "Detect automatically";

    TransactionsView(MapleCfoFacade app) {
        this.app = app;
        this.month = YearMonth.from(app.today());

        Button importBtn = Ui.primary("Import CSV…");
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
        table.getColumns().add(Ui.column("Category", t -> Ui.pretty(t.getCategory()), 130));
        table.getColumns().add(Ui.column("Set by", t -> t.getCategorySource().name().toLowerCase(), 90));
        table.getColumns().add(Ui.column("Account", Transaction::getAccountId, 100));
        VBox.setVgrow(table, Priority.ALWAYS);

        newCategory.getItems().setAll(List.of(Category.values()).stream()
                .filter(c -> c != Category.UNCATEGORIZED).toList());
        newCategory.setPromptText("New category");
        Button apply = new Button("Change category");
        apply.setOnAction(e -> Ui.attempt(this::recategorize));

        status.setWrapText(true);
        javafx.scene.layout.Region grow = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(grow, Priority.ALWAYS);
        root = Ui.page(
                Ui.card(Ui.heading("Import a bank CSV"),
                        Ui.form(Ui.muted("Account"), accountId, Ui.muted("Bank format"), profile, importBtn), status),
                Ui.row(prev, monthLabel, next, grow, search, reviewOnly),
                table,
                Ui.card(Ui.form(Ui.muted("Selected transaction"), newCategory, apply,
                        Ui.muted("MapleCFO remembers the merchant next time. Undo reverts it."))));
    }

    @Override public String title() { return "Transactions"; }

    @Override public String subtitle() { return "Import bank CSVs and fix categories"; }

    @Override public String icon() { return Icons.LIST; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        String selected = profile.getValue();
        List<String> formats = new java.util.ArrayList<>();
        formats.add(AUTO);
        app.getMappingProfiles().stream().map(ColumnMapping::profileName).forEach(formats::add);
        profile.getItems().setAll(formats);
        profile.setValue(selected != null && formats.contains(selected) ? selected : AUTO);

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
            String format = profile.getValue();
            String how = "";
            if (format == null || AUTO.equals(format)) {
                format = app.detectImportFormat(f.toPath()).orElseThrow(() -> new IllegalArgumentException(
                        "MapleCFO could not recognise the columns in " + f.getName()
                                + ". Choose the bank format from the list and try again."));
                how = " (detected automatically)";
            }
            ImportResult r = app.importTransactions(f.toPath(), accountId.getText().trim(), format);
            status.setText(f.getName() + " — format: " + format + how + ". " + r);
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
