package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.FireCalculator;
import com.maplecfo.planning.FireInputs;
import com.maplecfo.planning.FireProjection;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * F07 FIRE: when could you stop working, and what changes if you save or spend more. Your own target, a target
 * age, the expected return and the withdrawal rate can all be changed; the last plan is remembered.
 */
final class RetirementView implements View {

    static final String RULE_TARGET = "Yearly spending ÷ withdrawal rate";
    static final String OWN_TARGET = "My own amount";

    private final MapleCfoFacade app;
    private final TextField invested = Ui.field("e.g. 10,000");
    private final TextField save = Ui.field("e.g. 15,000");
    private final TextField spend = Ui.field("e.g. 40,000");
    private final TextField age = Ui.field("e.g. 23");
    private final ComboBox<String> targetKind = new ComboBox<>();
    private final TextField target = Ui.field("e.g. 1,000,000");
    private final TextField retireAge = Ui.field("Optional, e.g. 50");
    private final TextField returnPct = Ui.field("5");
    private final TextField withdrawalPct = Ui.field("4");
    private final Label status = Ui.small("");
    private final VBox result = new VBox(16);
    private final ScrollPane scroll;
    private boolean filled;

    RetirementView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        invested.setText("10000");
        save.setText("15000");
        spend.setText("40000");
        returnPct.setText("5");
        withdrawalPct.setText("4");
        targetKind.getItems().setAll(RULE_TARGET, OWN_TARGET);
        targetKind.setValue(RULE_TARGET);
        target.disableProperty().bind(targetKind.valueProperty().isEqualTo(RULE_TARGET));

        Button project = Ui.primary("Show my plan");
        project.setOnAction(e -> project());
        Button mine = Ui.secondary("Use my numbers");
        mine.setOnAction(e -> Ui.attempt(this::useMyNumbers));
        Ui.Form basics = new Ui.Form()
                .add("Invested today ($)", invested, "TFSA, RRSP, FHSA and other investments together.")
                .add("You save per year ($)", save, null)
                .add("You'd spend per year once retired ($)", spend, "Today's dollars.")
                .add("Your age", age, null);
        Ui.Form advanced = new Ui.Form()
                .add("Target", targetKind, "The usual rule: with a 4% withdrawal rate the target is 25 × your spending.")
                .add("Your own target ($)", target, "Only used when Target is \"" + OWN_TARGET + "\".")
                .add("Age you'd like to retire at", retireAge, "MapleCFO works out how much you'd need to save per year.")
                .add("Expected return after inflation (%)", returnPct, "Between -5 and 15. A diversified portfolio is "
                        + "often assumed to earn 4 to 6% above inflation over the long run; it is not guaranteed.")
                .add("Withdrawal rate (%)", withdrawalPct, "Between 2 and 6. Lower is safer: 3.5% means a bigger target.");
        TitledPane more = new TitledPane("Target, retirement age and assumptions", advanced.grid);
        more.setExpanded(false);
        more.setAnimated(false);
        VBox card = Ui.card("Financial independence (FIRE)", "You can stop working when your investments can pay "
                + "for your yearly spending. Change any number and press Show my plan.", null,
                basics.grid, more, Ui.row(project, mine, status));
        card.setMaxWidth(600);
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
        if (!filled) {
            filled = true;
            app.lastFireInputs().ifPresentOrElse(in -> {
                show(in);
                status.setText("Your last plan.");
            }, () -> {
                Integer born = app.getProfile().getBirthYear();
                age.setText(String.valueOf(born != null ? app.today().getYear() - born : 22));
            });
        }
    }

    /** Fills the form from your accounts and recent cash flow, then shows the plan. */
    void useMyNumbers() {
        FireInputs in = app.suggestFireInputs();
        invested.setText(plain(in.invested()));
        save.setText(plain(in.yearlySaving()));
        spend.setText(plain(in.annualSpending()));
        age.setText(String.valueOf(in.currentAge()));
        status.setText("Filled in from your accounts and the last few months.");
        project();
    }

    private void show(FireInputs in) {
        invested.setText(plain(in.invested()));
        save.setText(plain(in.yearlySaving()));
        spend.setText(plain(in.annualSpending()));
        age.setText(String.valueOf(in.currentAge()));
        returnPct.setText(pct(in.realReturn()));
        withdrawalPct.setText(pct(in.withdrawalRate()));
        targetKind.setValue(in.hasCustomTarget() ? OWN_TARGET : RULE_TARGET);
        target.setText(in.hasCustomTarget() ? plain(in.targetOverride()) : "");
        retireAge.setText(in.targetAge() == null ? "" : String.valueOf(in.targetAge()));
    }

    private static String plain(Money m) {
        return m.getAmount().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String pct(double rate) {
        return FireCalculator.percent(rate).replace("%", "");
    }

    private static double rate(TextField f, String what) {
        try {
            return Double.parseDouble(f.getText().trim().replace("%", "")) / 100;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number, for example 5.");
        }
    }

    private static Integer wholeOrNull(TextField f, String what) {
        String t = f.getText() == null ? "" : f.getText().trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a whole number.");
        }
    }

    FireInputs readInputs() {
        Integer a = wholeOrNull(age, "Age");
        if (a == null) throw new IllegalArgumentException("Enter your age.");
        Money own = OWN_TARGET.equals(targetKind.getValue()) ? Ui.money(target, "your own target") : null;
        return new FireInputs(Ui.moneyOrZero(invested, "the invested amount"),
                Ui.money(save, "how much you save per year"), Ui.money(spend, "your yearly spending"),
                rate(returnPct, "The expected return"), rate(withdrawalPct, "The withdrawal rate"), a, own,
                wholeOrNull(retireAge, "The age you'd like to retire at"));
    }

    void project() {
        Ui.attempt(() -> {
            FireInputs in = readInputs();
            FireProjection p = app.projectFire(in);
            Label head = new Label(p.reachable() ? "You could retire in " + p.yearsToFire() + " years, at age "
                    + p.fireAge() + "." : "Not reachable within 70 years with these numbers.");
            head.getStyleClass().add(Styles.TITLE_3);
            head.setWrapText(true);
            String how = in.hasCustomTarget() ? "your own target"
                    : FireCalculator.percent(1 / in.withdrawalRate() / 100).replace("%", "") + " × your yearly spending";
            ResponsiveGrid figures = new ResponsiveGrid(170, 12,
                    Ui.stat("Target", Ui.big(p.targetAmount().toString()), Ui.small(how)),
                    Ui.stat("Lets you spend", Ui.big(p.safeYearlySpending().toString()),
                            Ui.small("a year at a " + FireCalculator.percent(in.withdrawalRate()) + " withdrawal rate")),
                    Ui.stat("Coast FIRE number", Ui.big(p.coastNumber().toString()),
                            Ui.small(p.coastReached() ? "Reached: growth alone gets you there by " + p.coastAge()
                                    : "Invested today that would grow to the target by " + p.coastAge())));
            VBox top = Ui.card(head, figures);
            if (p.savingNeeded() != null) {
                boolean enough = !in.yearlySaving().isLessThan(p.savingNeeded());
                top.getChildren().add(Ui.banner(p.savingNeeded().isZero()
                        ? "Your investments reach the target by age " + in.targetAge() + " without saving more."
                        : "To retire at " + in.targetAge() + " you'd need to save about " + p.savingNeeded()
                                + " a year. You save " + in.yearlySaving() + (enough ? ", so you're on track."
                                : ", so that's " + p.savingNeeded().minus(in.yearlySaving()) + " a year more."),
                        enough ? "ok" : "warn"));
            }
            if (in.hasCustomTarget() && p.safeYearlySpending().isLessThan(in.annualSpending())) {
                top.getChildren().add(Ui.banner("Your target lets you spend " + p.safeYearlySpending() + " a year, less "
                        + "than the " + in.annualSpending() + " you plan to spend.", "warn"));
            }

            TableView<FireProjection.Scenario> t = Ui.table("");
            t.getColumns().add(Ui.column("If…", FireProjection.Scenario::label, 210));
            t.getColumns().add(Ui.moneyColumn("Save / year", FireProjection.Scenario::yearlySaving, 110, false));
            t.getColumns().add(Ui.moneyColumn("Spend / year", FireProjection.Scenario::annualSpending, 110, false));
            t.getColumns().add(Ui.moneyColumn("Target", FireProjection.Scenario::targetAmount, 130, false));
            t.getColumns().add(Ui.column("Years", s -> s.reachable() ? s.yearsToFire() : "—", 70));
            t.getItems().setAll(p.scenarios());
            Ui.fitRows(t, 6);

            NumberAxis ages = new NumberAxis();
            ages.setForceZeroInRange(false);
            ages.setMinorTickVisible(false);
            ages.setTickLabelFormatter(new javafx.util.StringConverter<>() {
                @Override public String toString(Number n) {
                    return n.doubleValue() == Math.rint(n.doubleValue()) ? String.valueOf(n.intValue()) : "";
                }
                @Override public Number fromString(String s) { return Integer.valueOf(s); }
            });
            LineChart<Number, Number> chart = new LineChart<>(ages, new NumberAxis());
            chart.getStyleClass().add("fire-chart");
            chart.setCreateSymbols(false);
            chart.setAnimated(false);
            chart.setPrefHeight(280);
            chart.getXAxis().setLabel("Age");
            chart.getYAxis().setLabel("Invested ($)");
            XYChart.Series<Number, Number> balance = new XYChart.Series<>();
            balance.setName("Your investments");
            XYChart.Series<Number, Number> goal = new XYChart.Series<>();
            goal.setName("Target");
            for (int i = 0; i < p.yearlyBalances().size(); i++) {
                balance.getData().add(new XYChart.Data<>(in.currentAge() + i, p.yearlyBalances().get(i).getAmount()));
            }
            goal.getData().add(new XYChart.Data<>(in.currentAge(), p.targetAmount().getAmount()));
            goal.getData().add(new XYChart.Data<>(in.currentAge() + p.yearlyBalances().size() - 1,
                    p.targetAmount().getAmount()));
            chart.getData().setAll(List.of(balance, goal));

            result.getChildren().setAll(top,
                    Ui.card("What if…", "Saving a little more, or markets doing a little better or worse.", null, t),
                    Ui.card("Your investments over time", null, null, chart),
                    Ui.small("Education, not advice. Returns are assumptions after inflation; real markets go up and down."));
        });
    }
}
