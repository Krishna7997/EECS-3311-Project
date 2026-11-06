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
    private final Label monthLabel = new Label();
    private final VBox reportBody = new VBox(16);
    private final VBox placeholder;
    private final Label income = Ui.big("-");
    private final Label spending = Ui.big("-");
    private final Label saved = Ui.big("-");
    private final Label rate = Ui.small("");
    private final Label narrative = new Label("Press Generate to build the summary.");
    private final Label narrativeSource = Ui.small("");
    private final VBox details = new VBox(4);
    private final PieChart pie = new PieChart();
    private final Button generate = Ui.primary("Create report", Icons.SPARK);
    private YearMonth month;

    ReportView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        this.month = YearMonth.from(app.today()).minusMonths(1);
        narrative.setWrapText(true);
        pie.setPrefSize(260, 260);
        pie.setMinSize(220, 220);
        pie.setMaxSize(260, 260);
        pie.setLabelsVisible(false);   // labels overlap on small slices; the list beside the chart has the text
        pie.setStartAngle(90);
        pie.setAnimated(false);
        Button prev = Ui.secondary("‹");
        prev.setOnAction(e -> { month = month.minusMonths(1); refresh(); });
        Button next = Ui.secondary("›");
        next.setOnAction(e -> { month = month.plusMonths(1); refresh(); });
        monthLabel.getStyleClass().addAll(atlantafx.base.theme.Styles.TITLE_4, "month-label");
        monthLabel.setMinWidth(130);
        monthLabel.setAlignment(javafx.geometry.Pos.CENTER);
        generate.setOnAction(e -> generateAsync());

        ResponsiveGrid figures = new ResponsiveGrid(200, 16, Ui.stat("Income", income, null),
                Ui.stat("Spending", spending, null), Ui.stat("Saved", saved, rate));
        pie.setLegendVisible(false);
        narrative.setStyle("-fx-font-size: 14.5px; -fx-line-spacing: 3px;");

        reportBody.getChildren().addAll(figures, Ui.card("Summary", null, null, narrative, narrativeSource),
                Ui.card("Where the money went", null, null, breakdownRow()), Ui.card("Details", null, null, details));
        Button create = Ui.primary("Create report", Icons.SPARK);
        create.setOnAction(e -> generateAsync());
        placeholder = Ui.emptyState(Icons.REPORT, "Your monthly report",
                "Choose a month with ‹ › and press Create report. You get income, spending, savings rate, where the "
                        + "money went and a short summary written by the AI.", create);
        javafx.scene.layout.StackPane holder = new javafx.scene.layout.StackPane(placeholder);
        this.holder = holder;
        root = Ui.scroll(Ui.page(Ui.row(prev, monthLabel, next, Ui.spacer(), generate), holder));
    }

    private final javafx.scene.layout.StackPane holder;
    private YearMonth shown;

    @Override public String id() { return Nav.REPORT; }
    @Override public String title() { return "Monthly report"; }
    @Override public String subtitle() { return "A summary of any month, written by the AI when available"; }
    @Override public String section() { return "Reports"; }
    @Override public String icon() { return Icons.REPORT; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        monthLabel.setText(month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.CANADA)
                + " " + month.getYear());
        holder.getChildren().setAll(month.equals(shown) ? reportBody : placeholder);
    }

    private void generateAsync() {
        generate.setDisable(true);
        narrative.setText("Building the summary…");
        holder.getChildren().setAll(reportBody);
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

    /** Fixed colours so a category keeps the same colour in the chart and in the list. */
    static final String[] PALETTE = {"#c8102e", "#2f6fdb", "#1f8a5b", "#e0a12b", "#7b4fc9", "#13a3b5", "#e2602f",
            "#8a90a0", "#d14f8f", "#5b6170"};

    private final javafx.scene.layout.VBox breakdown = new javafx.scene.layout.VBox(8);

    private javafx.scene.Node breakdownRow() {
        javafx.scene.layout.HBox.setHgrow(breakdown, javafx.scene.layout.Priority.ALWAYS);
        breakdown.setMinWidth(0);
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(24, pie, breakdown);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    /** Pie (no labels) plus a list: colour dot, category, bar, amount and share, biggest first. */
    private void showBreakdown(MonthlyReport r) {
        pie.getData().clear();
        breakdown.getChildren().clear();
        double total = r.spendingByCategory().values().stream().mapToDouble(m -> m.getAmount().doubleValue()).sum();
        java.util.List<Map.Entry<Category, Money>> rows = r.spendingByCategory().entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())).toList();
        if (rows.isEmpty()) {
            breakdown.getChildren().add(Ui.muted("No spending this month."));
            return;
        }
        int i = 0;
        for (Map.Entry<Category, Money> e : rows) {
            String colour = PALETTE[i++ % PALETTE.length];
            double amount = e.getValue().getAmount().doubleValue();
            PieChart.Data d = new PieChart.Data(Ui.pretty(e.getKey()), amount);
            pie.getData().add(d);
            String style = "-fx-pie-color: " + colour + ";";
            if (d.getNode() != null) d.getNode().setStyle(style);
            d.nodeProperty().addListener((o, was, node) -> { if (node != null) node.setStyle(style); });

            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(5, javafx.scene.paint.Color.web(colour));
            Label name = new Label(Ui.pretty(e.getKey()));
            name.setMinWidth(120);
            name.setPrefWidth(120);
            javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(total > 0 ? amount / total : 0);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setStyle("-fx-accent: " + colour + ";");
            bar.getStyleClass().add("plain");
            javafx.scene.layout.HBox.setHgrow(bar, javafx.scene.layout.Priority.ALWAYS);
            Label value = new Label(e.getValue().toString());
            value.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            value.setStyle("-fx-font-weight: bold;");
            Label share = Ui.muted(total > 0 ? Math.round(amount / total * 100) + "%" : "");
            share.setMinWidth(40);
            share.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            javafx.scene.layout.HBox line = new javafx.scene.layout.HBox(10, dot, name, bar, value, share);
            line.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            breakdown.getChildren().add(line);
        }
    }

    /** Package-private for the smoke test. */
    void show(MonthlyReport r) {
        shown = r.month();
        month = r.month();
        refresh();
        income.setText(r.income().toString());
        spending.setText(r.spending().toString());
        saved.setText(r.saved().toString());
        rate.setText(Math.round(r.savingsRatePercent()) + "% savings rate");
        narrative.setText(r.narrative());
        narrativeSource.setText(r.narrativeByAi() ? "Written by the AI and checked against the figures above."
                : "Written from a template (no AI available or the AI text could not be verified).");
        showBreakdown(r);
        details.setFillWidth(true);
        details.getChildren().setAll(
                new Label(r.transactionCount() + " transactions. Spending changed by " + r.spendingChange()
                        + " compared with the month before."),
                new Label("Recurring charges: about " + r.recurringPerMonth() + " a month."));
        for (String s : r.priceIncreases()) details.getChildren().add(new Label("Price increase: " + s));
        for (BudgetStatus b : r.budgets()) {
            details.getChildren().add(new Label("Budget " + Ui.pretty(b.budget().category()) + ": " + b.spent() + " of "
                    + b.budget().limit() + (b.isExceeded() ? " (over)" : "")));
        }
        details.getChildren().forEach(n -> ((Label) n).setWrapText(true));
        if (!r.hasData()) narrative.setText("No transactions for " + r.month() + ". Import a CSV first.");
    }
}
