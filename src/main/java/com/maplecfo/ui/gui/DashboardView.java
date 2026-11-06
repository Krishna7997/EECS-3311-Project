package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.planning.GoalForecast;
import com.maplecfo.planning.NetWorthSnapshot;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Home screen: net worth, this month's cash flow, budgets, goals and alerts at a glance. */
final class DashboardView implements View {

    private final MapleCfoFacade app;
    private final Label netWorth = Ui.big("-");
    private final Label netWorthDetail = Ui.muted("");
    private final Label monthSpent = Ui.big("-");
    private final Label monthDetail = Ui.muted("");
    private final VBox budgetBox = new VBox(6);
    private final VBox goalBox = new VBox(6);
    private final ListView<String> alerts = new ListView<>();
    private final LineChart<String, Number> history = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final ScrollPane root;

    DashboardView(MapleCfoFacade app) {
        this.app = app;
        alerts.setPrefHeight(150);
        alerts.setPlaceholder(new Label("No alerts."));
        history.setLegendVisible(false);
        history.setPrefHeight(220);
        history.setAnimated(false);
        history.setTitle("Net worth history");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(Ui.card(Ui.heading("Net worth"), netWorth, netWorthDetail), 0, 0);
        grid.add(Ui.card(Ui.heading("Spent this month"), monthSpent, monthDetail), 1, 0);
        grid.add(Ui.card(Ui.heading("Budgets"), budgetBox), 0, 1);
        grid.add(Ui.card(Ui.heading("Savings goals"), goalBox), 1, 1);
        javafx.scene.layout.ColumnConstraints half = new javafx.scene.layout.ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);

        root = new ScrollPane(Ui.page(grid, Ui.card(Ui.heading("Alerts"), alerts), history));
        root.setFitToWidth(true);
    }

    @Override public String title() { return "Dashboard"; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        if (app.getAccounts().isEmpty()) {
            netWorth.setText("-");
            netWorthDetail.setText("Add your first account in the Accounts & Goals tab.");
        } else {
            NetWorthSnapshot s = app.getNetWorth();
            netWorth.setText(s.total().toString());
            netWorthDetail.setText("Assets " + s.assets() + "  ·  Debts " + s.liabilities());
        }

        YearMonth m = YearMonth.from(app.today());
        Money spent = Money.zero();
        Money income = Money.zero();
        for (Transaction t : app.getTransactions(m)) {
            if (t.getCategory() == Category.TRANSFER) continue;
            if (t.isExpense()) spent = spent.plus(t.getAmount().abs());
            else income = income.plus(t.getAmount());
        }
        monthSpent.setText(spent.toString());
        monthDetail.setText("Income " + income + " in " + m + ". Import a CSV in the Transactions tab to update.");

        budgetBox.getChildren().clear();
        List<BudgetStatus> budgets = app.getBudgetStatus(m);
        if (budgets.isEmpty()) budgetBox.getChildren().add(Ui.muted("No budgets for " + m + "."));
        for (BudgetStatus b : budgets) {
            ProgressBar bar = new ProgressBar(Math.min(1.0, b.percentUsed() / 100));
            bar.setPrefWidth(160);
            if (b.isExceeded()) bar.getStyleClass().add("over");
            else if (b.isNearLimit()) bar.getStyleClass().add("near");
            budgetBox.getChildren().add(Ui.row(fixed(b.budget().category().name()), bar,
                    Ui.muted(b.spent() + " of " + b.budget().limit())));
        }

        goalBox.getChildren().clear();
        List<GoalForecast> goals = app.forecastGoals();
        if (goals.isEmpty()) goalBox.getChildren().add(Ui.muted("No goals yet."));
        for (GoalForecast f : goals) {
            ProgressBar bar = new ProgressBar(f.goal().progress());
            bar.setPrefWidth(160);
            if (!f.onTrack()) bar.getStyleClass().add("near");
            goalBox.getChildren().add(Ui.row(fixed(f.goal().getName()), bar,
                    Ui.muted(f.isComplete() ? "done" : f.onTrack() ? "on track" : "behind")));
        }

        alerts.getItems().setAll(app.getAlerts().stream()
                .map(a -> a.severity() + ": " + a.message()).toList().reversed());

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<LocalDate, Money> e : app.getNetWorthHistory().entrySet()) {
            series.getData().add(new XYChart.Data<>(e.getKey().toString(), e.getValue().getAmount()));
        }
        history.getData().setAll(List.of(series));
        history.setVisible(series.getData().size() > 1);
        history.setManaged(series.getData().size() > 1);
    }

    private static Label fixed(String text) {
        Label l = new Label(text);
        l.setMinWidth(130);
        l.setPrefWidth(130);
        return l;
    }
}
