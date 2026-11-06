package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.planning.FireInputs;
import com.maplecfo.planning.FireProjection;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.List;

/** F07 FIRE: when could you stop working, and what changes if you save or spend more. */
final class RetirementView implements View {

    private final MapleCfoFacade app;
    private final TextField invested = Ui.field("e.g. 10,000");
    private final TextField save = Ui.field("e.g. 15,000");
    private final TextField spend = Ui.field("e.g. 40,000");
    private final TextField age = Ui.field("e.g. 23");
    private final VBox result = new VBox(16);
    private final ScrollPane scroll;

    RetirementView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        invested.setText("10000");
        save.setText("15000");
        spend.setText("40000");
        Button project = Ui.primary("Show my plan");
        project.setOnAction(e -> project());
        Ui.Form form = new Ui.Form()
                .add("Invested today ($)", invested, "TFSA, RRSP and other investments together.")
                .add("You save per year ($)", save, null)
                .add("You'd spend per year once retired ($)", spend, "Today's dollars.")
                .add("Your age", age, null);
        VBox card = Ui.card("Financial independence (FIRE)", "Uses a 5% yearly return after inflation and the "
                + "4% rule: you can retire when your investments are 25 times your yearly spending.", null,
                form.grid, project);
        card.setMaxWidth(560);
        scroll = Ui.scroll(Ui.page(card, result));
    }

    @Override public String id() { return Nav.RETIREMENT; }
    @Override public String title() { return "Retirement (FIRE)"; }
    @Override public String subtitle() { return "When could you stop working?"; }
    @Override public String section() { return "Plan"; }
    @Override public String icon() { return Icons.PLAN; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        if (age.getText().isBlank()) {
            Integer born = app.getProfile().getBirthYear();
            age.setText(String.valueOf(born != null ? app.today().getYear() - born : 22));
        }
    }

    private void project() {
        Ui.attempt(() -> {
            int a;
            try {
                a = Integer.parseInt(age.getText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Age must be a whole number.");
            }
            FireProjection p = app.projectFire(new FireInputs(Ui.moneyOrZero(invested, "the invested amount"),
                    Ui.money(save, "how much you save per year"), Ui.money(spend, "your yearly spending"), 0.05, 0.04, a));
            Label head = new Label(p.reachable() ? "You could retire in " + p.yearsToFire() + " years, at age "
                    + p.fireAge() + "." : "Not reachable within 70 years with these numbers.");
            head.getStyleClass().add(Styles.TITLE_3);
            head.setWrapText(true);
            TableView<FireProjection.Scenario> t = Ui.table("");
            t.getColumns().add(Ui.column("If you…", FireProjection.Scenario::label, 200));
            t.getColumns().add(Ui.moneyColumn("Save / year", FireProjection.Scenario::yearlySaving, 120, false));
            t.getColumns().add(Ui.moneyColumn("Spend / year", FireProjection.Scenario::annualSpending, 120, false));
            t.getColumns().add(Ui.column("Years", s -> s.reachable() ? s.yearsToFire() : "—", 80));
            t.getItems().setAll(p.scenarios());
            t.setPrefHeight(200);
            LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
            chart.setLegendVisible(false);
            chart.setCreateSymbols(false);
            chart.setAnimated(false);
            chart.setPrefHeight(260);
            chart.getXAxis().setLabel("Years from now");
            chart.getYAxis().setLabel("Invested ($)");
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            for (int i = 0; i < p.yearlyBalances().size(); i++) {
                s.getData().add(new XYChart.Data<>(i, p.yearlyBalances().get(i).getAmount()));
            }
            chart.getData().setAll(List.of(s));
            result.getChildren().setAll(
                    Ui.card(head, Ui.muted("Target: " + p.targetAmount() + " (25 × your yearly spending)")),
                    Ui.card("What if…", "Small changes, big difference.", null, t),
                    Ui.card("Your investments over time", null, null, chart));
        });
    }
}
