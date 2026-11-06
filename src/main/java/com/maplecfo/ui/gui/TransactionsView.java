package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Transaction;
import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.ImportResult;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** F01 import and F02 categories: import a bank CSV, browse by month, fix a category right in the table. */
final class TransactionsView implements View {

    static final String AUTO = "Detect automatically";
    private static final List<Category> CHOICES = List.of(Category.values()).stream()
            .filter(c -> c != Category.UNCATEGORIZED).toList();

    private final MapleCfoFacade app;
    private final TableView<Transaction> table = Ui.table("No transactions in this month.");
    private final Label monthLabel = new Label();
    private final TextField search = Ui.field("Search, e.g. netflix");
    private final ToggleButton allBtn = new ToggleButton("This month");
    private final ToggleButton reviewBtn = new ToggleButton("Needs a category");
    private final VBox resultBox = new VBox();
    private final StackPane body = new StackPane();
    private final VBox root;
    private YearMonth month;

    TransactionsView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        this.month = YearMonth.from(app.today());

        Button importBtn = Ui.primary("Import bank CSV", Icons.UPLOAD);
        importBtn.setOnAction(e -> chooseAndImport());

        Button prev = Ui.secondary("‹");
        prev.setOnAction(e -> { month = month.minusMonths(1); allBtn.setSelected(true); refresh(); });
        Button next = Ui.secondary("›");
        next.setOnAction(e -> { month = month.plusMonths(1); allBtn.setSelected(true); refresh(); });
        monthLabel.getStyleClass().addAll(Styles.TITLE_4, "month-label");
        monthLabel.setMinWidth(130);
        monthLabel.setAlignment(Pos.CENTER);

        ToggleGroup filter = new ToggleGroup();
        allBtn.setToggleGroup(filter);
        reviewBtn.setToggleGroup(filter);
        allBtn.getStyleClass().add(Styles.LEFT_PILL);
        reviewBtn.getStyleClass().add(Styles.RIGHT_PILL);
        allBtn.setSelected(true);
        filter.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) a.setSelected(true);
            refresh();
        });
        search.setPrefWidth(220);
        search.textProperty().addListener((o, a, b) -> refresh());

        HBox toolbar = new HBox(10, prev, monthLabel, next, new HBox(0, allBtn, reviewBtn), Ui.spacer(), search, importBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().add(Ui.column("Date", Transaction::getDate, 110));
        table.getColumns().add(Ui.column("Description", Transaction::getDescription, 330));
        table.getColumns().add(Ui.moneyColumn("Amount", Transaction::getAmount, 120, true));
        table.getColumns().add(categoryColumn());
        table.getColumns().add(Ui.column("Account", Transaction::getAccountId, 110));
        VBox.setVgrow(body, Priority.ALWAYS);

        Label hint = Ui.small("Change a category with the drop-down in the table. MapleCFO remembers the merchant "
                + "for next time, and Undo at the top reverts it.");
        resultBox.managedProperty().bind(javafx.beans.binding.Bindings.isNotEmpty(resultBox.getChildren()));
        root = Ui.page(toolbar, resultBox, body, hint);
    }

    @Override public String id() { return Nav.TRANSACTIONS; }
    @Override public String title() { return "Transactions"; }
    @Override public String subtitle() { return "Import CSV exports from your bank and check the categories"; }
    @Override public String section() { return "Money"; }
    @Override public String icon() { return Icons.LIST; }
    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        monthLabel.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.CANADA) + " " + month.getYear());
        int review = app.getTransactionsNeedingReview().size();
        reviewBtn.setText("Needs a category" + (review > 0 ? " (" + review + ")" : ""));
        if (app.searchTransactions("").isEmpty()) {
            Button imp = Ui.primary("Import bank CSV", Icons.UPLOAD);
            imp.setOnAction(e -> chooseAndImport());
            body.getChildren().setAll(Ui.emptyState(Icons.UPLOAD, "No transactions yet",
                    "Log in to your bank's website, download your transactions as a CSV file, and import it here. "
                            + "TD, RBC and simple Date/Description/Amount files are recognised automatically. "
                            + "To try MapleCFO, use the files in the sample-data folder.", imp));
            return;
        }
        String q = search.getText() == null ? "" : search.getText().trim();
        List<Transaction> rows;
        if (reviewBtn.isSelected()) {
            rows = app.getTransactionsNeedingReview();
            table.setPlaceholder(Ui.muted("Every transaction has a category."));
        } else if (!q.isEmpty()) {
            rows = app.searchTransactions(q);
            table.setPlaceholder(Ui.muted("Nothing matches \"" + q + "\"."));
        } else {
            rows = app.getTransactions(month);
            table.setPlaceholder(Ui.muted("No transactions in " + monthLabel.getText() + ". Use ‹ › to change month."));
        }
        table.getItems().setAll(rows);
        body.getChildren().setAll(table);
    }

    private TableColumn<Transaction, Transaction> categoryColumn() {
        TableColumn<Transaction, Transaction> c = new TableColumn<>("Category");
        c.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue()));
        c.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<Category> box = new ComboBox<>(FXCollections.observableArrayList(CHOICES));
            private boolean updating;

            {
                box.setConverter(new javafx.util.StringConverter<>() {
                    @Override public String toString(Category cat) { return cat == null ? "" : Ui.pretty(cat); }
                    @Override public Category fromString(String s) { return null; }
                });
                box.setPromptText("Choose…");
                box.getStyleClass().addAll(Styles.SMALL, "category-box");
                box.setMaxWidth(Double.MAX_VALUE);
                box.valueProperty().addListener((o, old, now) -> {
                    Transaction t = getItem();
                    if (updating || t == null || now == null || now == t.getCategory()) return;
                    Ui.attempt(() -> app.recategorize(t.getId(), now));
                    refresh();
                });
            }

            @Override
            protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                setText(null);
                if (empty || t == null) {
                    setGraphic(null);
                    return;
                }
                updating = true;
                box.setValue(t.getCategory() == Category.UNCATEGORIZED ? null : t.getCategory());
                box.getStyleClass().remove("needs-review");
                if (t.getCategory() == Category.UNCATEGORIZED) box.getStyleClass().add("needs-review");
                updating = false;
                setGraphic(box);
            }
        });
        c.setPrefWidth(170);
        c.setSortable(false);
        return c;
    }

    private void chooseAndImport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose the CSV file you downloaded from your bank");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv", "*.CSV"));
        File sample = new File("sample-data");
        if (sample.isDirectory()) fc.setInitialDirectory(sample);
        File f = fc.showOpenDialog(root.getScene().getWindow());
        if (f != null) {
            importDialog(f);
        }
    }

    /** Step 2 of importing: which account it belongs to (format is detected, can be changed). */
    private void importDialog(File f) {
        ComboBox<String> account = new ComboBox<>();
        account.setEditable(true);
        List<String> ids = new ArrayList<>(app.getAccounts().stream().map(Account::getId).toList());
        if (!ids.contains("chequing")) ids.add(0, "chequing");
        account.getItems().setAll(ids);
        account.setValue(guessAccount(f.getName(), ids));

        ComboBox<String> format = new ComboBox<>();
        format.getItems().add(AUTO);
        app.getMappingProfiles().stream().map(ColumnMapping::profileName).forEach(format.getItems()::add);
        String detected;
        try {
            detected = app.detectImportFormat(f.toPath()).orElse(null);
        } catch (java.io.IOException e) {
            detected = null;
        }
        format.setValue(AUTO);
        String help = detected != null ? "Detected: " + detected + ". Change it only if the result looks wrong."
                : "MapleCFO could not recognise these columns. Choose your bank's format.";

        Ui.Form form = new Ui.Form()
                .add("Account", account, "Which account is this file from? Type a short name like chequing or visa.")
                .add("Bank format", format, help);
        String detectedFinal = detected;
        Ui.dialog("Import " + f.getName(), null, form, "Import", () -> {
            String acc = account.getEditor().getText() == null ? "" : account.getEditor().getText().trim();
            if (acc.isEmpty()) throw new IllegalArgumentException("Enter the account this file belongs to.");
            String fmt = AUTO.equals(format.getValue()) ? detectedFinal : format.getValue();
            if (fmt == null) throw new IllegalArgumentException("Choose the bank format for this file.");
            ImportResult r = app.importTransactions(f.toPath(), acc, fmt);
            showResult(f.getName(), fmt, r);
        });
        refresh();
    }

    private static String guessAccount(String fileName, List<String> ids) {
        String n = fileName.toLowerCase();
        return ids.stream().filter(id -> n.contains(id.toLowerCase())).findFirst().orElse(ids.get(0));
    }

    private void showResult(String file, String format, ImportResult r) {
        String text = "Imported " + r.imported() + " transactions from " + file + " (" + format + ")."
                + (r.duplicatesSkipped() > 0 ? " Skipped " + r.duplicatesSkipped() + " already imported." : "")
                + (r.uncategorized() > 0 ? " " + r.uncategorized() + " need a category." : "")
                + (r.rejectedRows().isEmpty() ? "" : " " + r.rejectedRows().size() + " rows could not be read.");
        Button close = Ui.flat("×");
        close.setOnAction(e -> resultBox.getChildren().clear());
        resultBox.getChildren().setAll(Ui.banner(text, r.rejectedRows().isEmpty() ? "ok" : "warn", close));
        if (r.imported() > 0) {
            Transaction latest = app.searchTransactions("").stream()
                    .max(java.util.Comparator.comparing(Transaction::getDate)).orElse(null);
            if (latest != null) month = YearMonth.from(latest.getDate());
        }
    }

    /** For the smoke test: import without the file dialogs. */
    void importForTest(File f, String account) throws Exception {
        String fmt = app.detectImportFormat(f.toPath()).orElseThrow();
        showResult(f.getName(), fmt, app.importTransactions(f.toPath(), account, fmt));
        refresh();
    }
}
