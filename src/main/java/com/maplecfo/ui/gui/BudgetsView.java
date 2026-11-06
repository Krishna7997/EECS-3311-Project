package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.util.List;

/** F04 monthly budgets with progress bars; warnings appear as alerts on the dashboard. */
final class BudgetsView implements View {

    private final MapleCfoFacade app;
    private final VBox root;
    private final TableView<BudgetStatus> table = Ui.table("No budgets for this month yet.");
    private final ComboBox<Category> category = new ComboBox<>();
    private final TextField limit = Ui.field("Monthly limit, e.g. 250", 160);
    private final Label monthLabel = Ui.heading("");
    private YearMonth month;

    BudgetsView(MapleCfoFacade app) {
        this.app = app;
        this.month = YearMonth.from(app.today());
        category.getItems().setAll(List.of(Category.values()).stream()
                .filter(c -> c != Category.INCOME && c != Category.TRANSFER && c != Category.UNCATEGORIZED).toList());
        category.setPromptText("Category");
        Button set = new Button("Set budget");
        set.setOnAction(e -> Ui.attempt(() -> {
            if (category.getValue() == null) throw new IllegalArgumentException("Choose a category");
            app.setBudget(category.getValue(), Ui.money(limit, "a limit"), month);
            limit.clear();
            refresh();
        }));
        Button prev = new Button("◀");
        prev.setOnAction(e -> { month = month.minusMonths(1); refresh(); });
        Button next = new Button("▶");
        next.setOnAction(e -> { month = month.plusMonths(1); refresh(); });

        table.getColumns().add(Ui.column("Category", s -> s.budget().category(), 140));
        table.getColumns().add(Ui.column("Spent", BudgetStatus::spent, 110));
        table.getColumns().add(Ui.column("Limit", s -> s.budget().limit(), 110));
        table.getColumns().add(Ui.column("Left", BudgetStatus::remaining, 110));
        TableColumn<BudgetStatus, BudgetStatus> used = new TableColumn<>("Used");
        used.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        used.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BudgetStatus s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                    return;
                }
                ProgressBar bar = new ProgressBar(Math.min(1.0, s.percentUsed() / 100));
                bar.setPrefWidth(180);
                if (s.isExceeded()) bar.getStyleClass().add("over");
                else if (s.isNearLimit()) bar.getStyleClass().add("near");
                setGraphic(Ui.row(bar, new Label(Math.round(s.percentUsed()) + "%")));
            }
        });
        used.setPrefWidth(260);
        table.getColumns().add(used);
        VBox.setVgrow(table, Priority.ALWAYS);

        root = Ui.page(Ui.row(prev, monthLabel, next), table,
                Ui.row(category, limit, set, Ui.muted("You get a warning at 80% and when you go over.")));
    }

    @Override public String title() { return "Budgets"; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        monthLabel.setText(month.toString());
        table.getItems().setAll(app.getBudgetStatus(month));
    }
}
