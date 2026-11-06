package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.reports.MonthlyReport;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.util.Map;

/** F12 monthly summary: the figures, a spending pie chart and a short narrative (AI when available). */
final class ReportView implements View {

    private final MapleCfoFacade app;
    private final ScrollPane root;
    private final Label monthLabel = Ui.heading("");
    private final Label income = Ui.big("-");
    private final Label spending = Ui.big("-");
    private final Label saved = Ui.big("-");
    private final Label rate = Ui.muted("");
    private final Label narrative = new Label("Press Generate to build the summary.");
    private final Label narrativeSource = Ui.muted("");
    private final VBox details = new VBox(4);
    private final PieChart pie = new PieChart();
    private final Button generate = new Button("Generate summary");
    private YearMonth month;

    ReportView(MapleCfoFacade app) {
        this.app = app;
        this.month = YearMonth.from(app.today()).minusMonths(1);
        narrative.setWrapText(true);
        pie.setLegendVisible(true);
        pie.setPrefHeight(320);
        pie.setAnimated(false);
        Button prev = new Button("◀");
        prev.setOnAction(e -> { month = month.minusMonths(1); monthLabel.setText(month.toString()); });
        Button next = new Button("▶");
        next.setOnAction(e -> { month = month.plusMonths(1); monthLabel.setText(month.toString()); });
        generate.setOnAction(e -> generateAsync());

        GridPane figures = new GridPane();
        figures.setHgap(12);
        figures.add(Ui.card(Ui.heading("Income"), income), 0, 0);
        figures.add(Ui.card(Ui.heading("Spending"), spending), 1, 0);
        figures.add(Ui.card(Ui.heading("Saved"), saved, rate), 2, 0);

        root = new ScrollPane(Ui.page(Ui.row(prev, monthLabel, next, generate), figures,
                Ui.card(Ui.heading("Summary"), narrative, narrativeSource), pie, Ui.card(Ui.heading("Details"), details)));
        root.setFitToWidth(true);
    }

    @Override public String title() { return "Monthly Report"; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        monthLabel.setText(month.toString());
    }

    private void generateAsync() {
        generate.setDisable(true);
        narrative.setText("Building the summary…");
        YearMonth m = month;
        Task<MonthlyReport> task = new Task<>() {
            @Override
            protected MonthlyReport call() {
                return app.generateMonthlyReport(m);
            }
        };
        task.setOnSucceeded(e -> { show(task.getValue()); generate.setDisable(false); });
        task.setOnFailed(e -> { Ui.error(task.getException()); generate.setDisable(false); });
        Thread t = new Thread(task, "maplecfo-report");
        t.setDaemon(true);
        t.start();
    }

    /** Package-private for the smoke test. */
    void show(MonthlyReport r) {
        monthLabel.setText(r.month().toString());
        income.setText(r.income().toString());
        spending.setText(r.spending().toString());
        saved.setText(r.saved().toString());
        rate.setText(Math.round(r.savingsRatePercent()) + "% savings rate");
        narrative.setText(r.narrative());
        narrativeSource.setText(r.narrativeByAi() ? "Written by the AI and checked against the figures above."
                : "Written from a template (no AI available or the AI text could not be verified).");
        pie.getData().clear();
        for (Map.Entry<Category, Money> e : r.spendingByCategory().entrySet()) {
            pie.getData().add(new PieChart.Data(e.getKey().name() + " " + e.getValue(), e.getValue().getAmount().doubleValue()));
        }
        details.getChildren().setAll(
                new Label(r.transactionCount() + " transactions. Spending changed by " + r.spendingChange()
                        + " compared with the month before."),
                new Label("Recurring charges: about " + r.recurringPerMonth() + " a month."));
        for (String s : r.priceIncreases()) details.getChildren().add(new Label("Price increase: " + s));
        for (BudgetStatus b : r.budgets()) {
            details.getChildren().add(new Label("Budget " + b.budget().category() + ": " + b.spent() + " of "
                    + b.budget().limit() + (b.isExceeded() ? " (over)" : "")));
        }
        if (!r.hasData()) narrative.setText("No transactions for " + r.month() + ". Import a CSV first.");
    }
}
