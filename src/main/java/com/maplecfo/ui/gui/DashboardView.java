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
    private final VBox pendingBox = new VBox(6);
    private final VBox pendingCard;
    private final LineChart<String, Number> history = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final ScrollPane root;
    private final Label monthIncome = Ui.big("-");
    private final Label monthSaved = Ui.big("-");
    private final Label incomeDetail = new Label();
    private final Label savedDetail = new Label();
    private VBox historyCard;

    DashboardView(MapleCfoFacade app) {
        this.app = app;
        alerts.setPrefHeight(150);
        alerts.setPlaceholder(new Label("No alerts."));
        history.setLegendVisible(false);
        history.setPrefHeight(220);
        history.setAnimated(false);
        history.setCreateSymbols(true);

        netWorthDetail.getStyleClass().add("muted");
        monthDetail.getStyleClass().add("muted");
        incomeDetail.getStyleClass().add("muted");
        savedDetail.getStyleClass().add("muted");
        GridPane tiles = new GridPane();
        tiles.setHgap(16);
        tiles.add(Ui.stat("Net worth", netWorth, netWorthDetail), 0, 0);
        tiles.add(Ui.stat("Spent this month", monthSpent, monthDetail), 1, 0);
        tiles.add(Ui.stat("Income this month", monthIncome, incomeDetail), 2, 0);
        tiles.add(Ui.stat("Saved this month", monthSaved, savedDetail), 3, 0);
        for (int i = 0; i < 4; i++) {
            javafx.scene.layout.ColumnConstraints q = new javafx.scene.layout.ColumnConstraints();
            q.setPercentWidth(25);
            tiles.getColumnConstraints().add(q);
        }
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.add(Ui.card(Ui.heading("Budgets this month"), budgetBox), 0, 0);
        grid.add(Ui.card(Ui.heading("Savings goals"), goalBox), 1, 0);
        javafx.scene.layout.ColumnConstraints half = new javafx.scene.layout.ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);

        pendingCard = Ui.card(Ui.heading("Waiting for your OK"), pendingBox,
                Ui.muted("Found automatically (for example a TFSA transfer in your latest import). Nothing is saved until you confirm."));
        historyCard = Ui.card(Ui.heading("Net worth history"), history);
        root = new ScrollPane(Ui.page(tiles, pendingCard, grid, Ui.card(Ui.heading("Alerts"), alerts), historyCard));
        root.setFitToWidth(true);
    }

    @Override public String title() { return "Dashboard"; }

    @Override public String subtitle() { return "Your money at a glance"; }

    @Override public String icon() { return Icons.HOME; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        if (app.getAccounts().isEmpty()) {
            netWorth.setText("-");
            netWorthDetail.setText("Add accounts in Accounts & Goals");
        } else {
            NetWorthSnapshot s = app.getNetWorth();
            netWorth.setText(s.total().toString());
            netWorthDetail.setText("Assets " + s.assets() + " · Debts " + s.liabilities());
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
        monthDetail.setText(m.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.CANADA)
                + " " + m.getYear());
        monthIncome.setText(income.toString());
        incomeDetail.setText("Transfers not counted");
        Money saved = income.minus(spent);
        monthSaved.setText(saved.toString());
        monthSaved.getStyleClass().removeAll("positive", "negative");
        monthSaved.getStyleClass().add(saved.isNegative() ? "negative" : "positive");
        savedDetail.setText(income.isPositive()
                ? Math.round(saved.getAmount().doubleValue() / income.getAmount().doubleValue() * 100) + "% of income"
                : "No income yet this month");

        budgetBox.getChildren().clear();
        List<BudgetStatus> budgets = app.getBudgetStatus(m);
        if (budgets.isEmpty()) budgetBox.getChildren().add(Ui.muted("No budgets for " + m + "."));
        for (BudgetStatus b : budgets) {
            ProgressBar bar = new ProgressBar(Math.min(1.0, b.percentUsed() / 100));
            bar.setPrefWidth(160);
            if (b.isExceeded()) bar.getStyleClass().add("over");
            else if (b.isNearLimit()) bar.getStyleClass().add("near");
            budgetBox.getChildren().add(Ui.row(fixed(Ui.pretty(b.budget().category())), bar,
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
                    f.isComplete() ? Ui.chip("Done", "info") : f.onTrack() ? Ui.chip("On track", "ok")
                            : Ui.chip("Behind", "warn")));
        }

        pendingBox.getChildren().clear();
        app.getPendingActions().forEach((id, text) -> {
            javafx.scene.control.Button ok = Ui.primary("Confirm");
            ok.setOnAction(e -> Ui.attempt(() -> { app.confirmProposedAction(id); refresh(); }));
            javafx.scene.control.Button no = new javafx.scene.control.Button("Dismiss");
            no.setOnAction(e -> Ui.attempt(() -> { app.dismissProposedAction(id); refresh(); }));
            pendingBox.getChildren().add(Ui.row(new Label(id + ": " + text), ok, no));
        });
        pendingCard.setVisible(!pendingBox.getChildren().isEmpty());
        pendingCard.setManaged(!pendingBox.getChildren().isEmpty());

        alerts.getItems().setAll(app.getAlerts().stream()
                .map(a -> a.severity() + ": " + a.message()).toList().reversed());

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<LocalDate, Money> e : app.getNetWorthHistory().entrySet()) {
            series.getData().add(new XYChart.Data<>(e.getKey().toString(), e.getValue().getAmount()));
        }
        history.getData().setAll(List.of(series));
        historyCard.setVisible(series.getData().size() > 1);
        historyCard.setManaged(series.getData().size() > 1);
    }

    private static Label fixed(String text) {
        Label l = new Label(text);
        l.setMinWidth(130);
        l.setPrefWidth(130);
        return l;
    }
}
