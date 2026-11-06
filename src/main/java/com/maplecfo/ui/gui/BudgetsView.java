package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** F04 monthly budgets: one row per category with a progress bar; warnings at 80% and when over. */
final class BudgetsView implements View {

    static final List<Category> BUDGETABLE = List.of(Category.values()).stream()
            .filter(c -> c != Category.INCOME && c != Category.TRANSFER && c != Category.UNCATEGORIZED).toList();

    private final MapleCfoFacade app;
    private javafx.scene.control.ScrollPane scroll;
    private final Label monthLabel = new Label();
    private final VBox body = new VBox(16);
    private final VBox root;
    private YearMonth month;

    BudgetsView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        this.month = YearMonth.from(app.today());
        Button prev = Ui.secondary("‹");
        prev.setOnAction(e -> { month = month.minusMonths(1); refresh(); });
        Button next = Ui.secondary("›");
        next.setOnAction(e -> { month = month.plusMonths(1); refresh(); });
        monthLabel.getStyleClass().addAll(Styles.TITLE_4, "month-label");
        monthLabel.setMinWidth(130);
        monthLabel.setAlignment(Pos.CENTER);
        Button add = Ui.primary("Set a budget", Icons.PLUS);
        add.setOnAction(e -> openDialog(null));
        HBox toolbar = new HBox(10, prev, monthLabel, next, Ui.spacer(), add);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        root = Ui.page(toolbar, body);
        scroll = Ui.scroll(root);
    }

    @Override public String id() { return Nav.BUDGETS; }
    @Override public String title() { return "Budgets"; }
    @Override public String subtitle() { return "Monthly spending limits. You get a warning at 80% and when you go over"; }
    @Override public String section() { return "Money"; }
    @Override public String icon() { return Icons.BUDGET; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        monthLabel.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.CANADA) + " " + month.getYear());
        List<BudgetStatus> list = app.getBudgetStatus(month);
        body.getChildren().clear();
        if (list.isEmpty()) {
            Button add = Ui.primary("Set your first budget", Icons.PLUS);
            add.setOnAction(e -> openDialog(null));
            body.getChildren().add(Ui.emptyState(Icons.BUDGET, "No budgets for " + monthLabel.getText(),
                    "A budget is a monthly limit for one category, like $250 for dining. "
                            + "Start with the category where you spend the most (see below).", add));
        } else {
            VBox rows = new VBox(0);
            for (BudgetStatus s : list) rows.getChildren().add(budgetRow(s));
            Money limit = list.stream().map(s -> s.budget().limit()).reduce(Money.zero(), Money::plus);
            Money spent = list.stream().map(BudgetStatus::spent).reduce(Money.zero(), Money::plus);
            body.getChildren().add(Ui.card("Your budgets", spent + " spent of " + limit + " budgeted this month.",
                    null, rows));
        }
        body.getChildren().add(topSpending());
    }

    private Node budgetRow(BudgetStatus s) {
        Label name = new Label(Ui.pretty(s.budget().category()));
        name.getStyleClass().add(Styles.TEXT_BOLD);
        name.setMinWidth(130);
        ProgressBar bar = new ProgressBar(Math.min(1.0, s.percentUsed() / 100));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setMinWidth(80);
        HBox.setHgrow(bar, Priority.ALWAYS);
        if (s.isExceeded()) bar.getStyleClass().add("over");
        else if (s.isNearLimit()) bar.getStyleClass().add("near");
        Label amounts = new Label(s.spent() + " of " + s.budget().limit());
        amounts.setMinWidth(Region.USE_PREF_SIZE);
        Label left = Ui.small(s.isExceeded() ? s.remaining().abs() + " over" : s.remaining() + " left");
        left.setWrapText(false);
        left.setMinWidth(Region.USE_PREF_SIZE);
        Label chip = s.isExceeded() ? Ui.chip("Over", "bad") : s.isNearLimit() ? Ui.chip("Near limit", "warn")
                : Ui.chip("OK", "ok");
        Button edit = Ui.flat("Change");
        edit.setOnAction(e -> openDialog(s));
        HBox row = new HBox(14, name, bar, amounts, left, chip, edit);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }

    /** Where the money went this month, so the user knows which budget to set. */
    private Node topSpending() {
        Map<Category, Money> by = new TreeMap<>();
        for (Transaction t : app.getTransactions(month)) {
            if (t.isExpense() && BUDGETABLE.contains(t.getCategory())) {
                by.merge(t.getCategory(), t.getAmount().abs(), Money::plus);
            }
        }
        VBox rows = new VBox(0);
        by.entrySet().stream().sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(6).forEach(e -> {
            boolean has = app.getBudgetStatus(month).stream().anyMatch(s -> s.budget().category() == e.getKey());
            Label n = new Label(Ui.pretty(e.getKey()));
            n.setMinWidth(130);
            Label v = new Label(e.getValue().toString());
            HBox r = new HBox(14, n, v, Ui.spacer());
            if (!has) {
                Button set = Ui.flat("Set budget");
                set.setOnAction(ev -> openDialogFor(e.getKey()));
                r.getChildren().add(set);
            } else {
                r.getChildren().add(Ui.chip("Has a budget", "neutral"));
            }
            r.setAlignment(Pos.CENTER_LEFT);
            r.getStyleClass().add("list-row");
            rows.getChildren().add(r);
        });
        if (rows.getChildren().isEmpty()) rows.getChildren().add(Ui.muted("No spending recorded in this month."));
        return Ui.card("Where you spent the most", "Categories from your transactions in this month.", null, rows);
    }

    private void openDialogFor(Category c) {
        openDialog(null, c);
    }

    private void openDialog(BudgetStatus existing) {
        openDialog(existing, existing == null ? null : existing.budget().category());
    }

    private void openDialog(BudgetStatus existing, Category preset) {
        ComboBox<Category> category = new ComboBox<>();
        category.getItems().setAll(BUDGETABLE);
        category.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : Ui.pretty(c); }
            @Override public Category fromString(String s) { return null; }
        });
        category.setValue(preset);
        category.setPromptText("Choose a category");
        TextField limit = Ui.field("e.g. 250");
        if (existing != null) limit.setText(existing.budget().limit().getAmount().toPlainString());
        Ui.Form form = new Ui.Form()
                .add("Category", category, null)
                .add("Monthly limit ($)", limit, "You get a warning at 80% of this amount and an alert when you go over.");
        Ui.dialog(existing == null ? "Set a budget" : "Change budget",
                "For " + monthLabel.getText() + ". You can undo this later.", form, "Save", () -> {
                    if (category.getValue() == null) throw new IllegalArgumentException("Choose a category.");
                    Money m = Ui.money(limit, "a monthly limit");
                    if (!m.isPositive()) throw new IllegalArgumentException("The limit must be more than $0.");
                    app.setBudget(category.getValue(), m, month);
                }, this::refresh);
    }
}
