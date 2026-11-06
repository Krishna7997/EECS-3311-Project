package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.planning.ContributionCheck;
import com.maplecfo.planning.ContributionRoom;
import com.maplecfo.planning.FireInputs;
import com.maplecfo.planning.FireProjection;
import com.maplecfo.planning.PayoffMethod;
import com.maplecfo.planning.PayoffPlan;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.List;

/** F05 recurring charges, F06 registered accounts, F07 FIRE and F08 debt payoff. */
final class PlanningView implements View {

    private final MapleCfoFacade app;
    private final TabPane root = new TabPane();

    // F05
    private final TableView<RecurringCharge> recurring = Ui.table("No recurring charges found. Import a few months of transactions.");
    private final Label recurringTotal = Ui.muted("");

    // F06
    private final VBox roomBox = new VBox(6);
    private final TextField birthYear = Ui.field("Birth year", 90);
    private final TextField residentSince = Ui.field("Resident since", 110);
    private final TextField rrspLimit = Ui.field("RRSP deduction limit (NOA)", 190);
    private final TextField fhsaOpened = Ui.field("FHSA opened (year)", 130);
    private final ComboBox<AccountType> roomType = new ComboBox<>();
    private final TextField roomAmount = Ui.field("Amount", 100);
    private final Label roomResult = Ui.muted("");

    // F07
    private final TextField invested = Ui.field("Invested today", 120);
    private final TextField save = Ui.field("Saved per year", 120);
    private final TextField spend = Ui.field("Spending per year", 130);
    private final TextField age = Ui.field("Age", 60);
    private final Label fireResult = Ui.big("");
    private final TableView<FireProjection.Scenario> scenarios = Ui.table("Enter your numbers and press Project.");
    private final LineChart<Number, Number> fireChart = new LineChart<>(new NumberAxis(), new NumberAxis());

    // F08
    private final TableView<Debt> debts = Ui.table("No debts. Add one below.");
    private final TextField debtName = Ui.field("Name, e.g. RBC Visa", 150);
    private final TextField debtBalance = Ui.field("Balance", 100);
    private final TextField debtApr = Ui.field("APR %", 70);
    private final TextField debtMin = Ui.field("Min payment", 100);
    private final TextField extra = Ui.field("Extra per month", 120);
    private final TableView<PayoffPlan> plans = Ui.table("Press Compare to see both methods.");

    PlanningView(MapleCfoFacade app) {
        this.app = app;
        root.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.getTabs().addAll(new Tab("Recurring charges", recurringPage()), new Tab("TFSA / RRSP / FHSA", roomPage()),
                new Tab("Debt payoff", debtPage()), new Tab("FIRE", firePage()));
    }

    @Override public String title() { return "Planning"; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        List<RecurringCharge> charges = app.getRecurringCharges();
        recurring.getItems().setAll(charges);
        Money perMonth = charges.stream().map(c -> c.yearlyCost().dividedBy(12)).reduce(Money.zero(), Money::plus);
        recurringTotal.setText("About " + perMonth + " a month in recurring charges."
                + (app.isRecurringLowConfidence() ? " (Low confidence: import at least 3 months.)" : ""));

        UserProfile p = app.getProfile();
        setIfEmpty(birthYear, p.getBirthYear());
        setIfEmpty(residentSince, p.getResidentSinceYear());
        setIfEmpty(rrspLimit, p.getRrspDeductionLimit() == null ? null : p.getRrspDeductionLimit().getAmount());
        setIfEmpty(fhsaOpened, p.getFhsaOpenedYear());
        roomBox.getChildren().clear();
        int year = app.today().getYear();
        for (AccountType t : List.of(AccountType.TFSA, AccountType.RRSP, AccountType.FHSA)) {
            List<String> missing = p.missingFieldsFor(t);
            if (!missing.isEmpty()) {
                roomBox.getChildren().add(Ui.row(new Label(t + ":"), Ui.muted("unknown, needs " + String.join(", ", missing))));
                continue;
            }
            ContributionRoom r = app.getContributionRoom(t, year);
            Label l = new Label(t + " " + year + ": " + r.available() + " available  (room " + r.limit() + ", used "
                    + r.used() + ")" + (r.isOverContributed() ? "  OVER-CONTRIBUTED" : ""));
            if (r.isOverContributed()) l.getStyleClass().add("verdict-no");
            roomBox.getChildren().add(l);
        }

        debts.getItems().setAll(app.getDebts());
    }

    // ------------------------------------------------------------------ pages

    private Node recurringPage() {
        recurring.getColumns().add(Ui.column("Merchant", RecurringCharge::merchant, 200));
        recurring.getColumns().add(Ui.column("Usual amount", RecurringCharge::averageAmount, 110));
        recurring.getColumns().add(Ui.column("How often", c -> c.frequency().name().toLowerCase(), 90));
        recurring.getColumns().add(Ui.column("Next expected", RecurringCharge::nextExpectedDate, 110));
        recurring.getColumns().add(Ui.column("Per year", RecurringCharge::yearlyCost, 110));
        recurring.getColumns().add(Ui.column("Note", c -> c.priceIncreased() ? "price went up"
                : c.variableAmount() ? "amount varies" : "", 120));
        return Ui.page(Ui.heading("Subscriptions and bills found in your transactions"), recurring, recurringTotal);
    }

    private Node roomPage() {
        Button saveProfile = new Button("Save profile");
        saveProfile.setOnAction(e -> Ui.attempt(() -> {
            UserProfile p = app.getProfile();
            p.setBirthYear(intOrNull(birthYear));
            p.setResidentSinceYear(intOrNull(residentSince));
            p.setRrspDeductionLimit(rrspLimit.getText().isBlank() ? null : Ui.money(rrspLimit, "the RRSP limit"));
            p.setFhsaOpenedYear(intOrNull(fhsaOpened));
            app.saveProfile(p);
            refresh();
        }));
        roomType.getItems().setAll(AccountType.TFSA, AccountType.RRSP, AccountType.FHSA);
        roomType.setValue(AccountType.TFSA);
        Button check = new Button("Check before contributing");
        check.setOnAction(e -> Ui.attempt(() -> {
            ContributionCheck c = app.checkContribution(roomType.getValue(), Ui.money(roomAmount, "an amount"));
            roomResult.setText(switch (c.status()) {
                case OK -> "OK: it fits. Room left afterwards: " + c.roomAfter() + ".";
                case OVER -> "OVER by " + c.excess() + ". CRA charges 1% per month on the excess.";
                case UNKNOWN -> "Unknown: fill in " + String.join(", ", c.missingFields()) + " above.";
            });
            roomResult.getStyleClass().setAll("label", c.status() == ContributionCheck.Status.OVER ? "verdict-no" : "muted");
        }));
        Button log = new Button("Record contribution");
        log.setOnAction(e -> Ui.attempt(() -> {
            Money amt = Ui.money(roomAmount, "an amount");
            app.recordContribution(new ContributionRecord(roomType.getValue(), app.today(), amt, ContributionKind.CONTRIBUTION));
            roomResult.setText("Recorded " + amt + " to " + roomType.getValue() + ".");
            refresh();
        }));
        ScrollPane sp = new ScrollPane(Ui.page(
                Ui.card(Ui.heading("Contribution room this year"), roomBox),
                Ui.card(Ui.heading("Your profile (used to calculate room)"),
                        Ui.row(birthYear, residentSince, rrspLimit, fhsaOpened, saveProfile),
                        Ui.muted("The RRSP deduction limit is on your latest CRA Notice of Assessment.")),
                Ui.card(Ui.heading("Check or record a contribution"), Ui.row(roomType, roomAmount, check, log), roomResult)));
        sp.setFitToWidth(true);
        return sp;
    }

    private Node debtPage() {
        debts.getColumns().add(Ui.column("Name", Debt::getName, 180));
        debts.getColumns().add(Ui.column("Balance", Debt::getBalance, 110));
        debts.getColumns().add(Ui.column("APR", d -> d.getApr() + "%", 80));
        debts.getColumns().add(Ui.column("Min / month", Debt::getMinPayment, 110));
        debts.setPrefHeight(180);
        Button add = new Button("Add debt");
        add.setOnAction(e -> Ui.attempt(() -> {
            double apr;
            try {
                apr = Double.parseDouble(debtApr.getText().trim().replace("%", ""));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("APR must be a number, e.g. 20.99");
            }
            app.addDebt(debtName.getText().trim(), Ui.money(debtBalance, "a balance"), apr, Ui.money(debtMin, "a minimum payment"));
            debtName.clear(); debtBalance.clear(); debtApr.clear(); debtMin.clear();
            refresh();
        }));
        Button remove = new Button("Remove selected");
        remove.setOnAction(e -> Ui.attempt(() -> {
            Debt d = debts.getSelectionModel().getSelectedItem();
            if (d == null) throw new IllegalArgumentException("Select a debt first");
            app.removeDebt(d.getId());
            refresh();
        }));
        plans.getColumns().add(Ui.column("Method", PayoffPlan::strategyName, 120));
        plans.getColumns().add(Ui.column("Debt-free in", p -> p.completes() ? p.monthsToDebtFree() + " months" : "never", 110));
        plans.getColumns().add(Ui.column("Debt-free by", p -> p.completes() ? p.debtFreeMonth() : "-", 100));
        plans.getColumns().add(Ui.column("Total interest", PayoffPlan::totalInterest, 120));
        plans.getColumns().add(Ui.column("Order paid off", p -> String.join(" → ",
                p.payoffs().stream().map(PayoffPlan.DebtPayoff::debtName).toList()), 220));
        plans.setPrefHeight(110);
        extra.setText("0");
        Button compare = new Button("Compare avalanche and snowball");
        Label warn = Ui.muted("");
        compare.setOnAction(e -> Ui.attempt(() -> {
            Money x = Ui.money(extra, "the extra payment");
            PayoffPlan a = app.planDebtPayoff(PayoffMethod.AVALANCHE, x);
            PayoffPlan s = app.planDebtPayoff(PayoffMethod.SNOWBALL, x);
            plans.getItems().setAll(a, s);
            warn.setText(String.join(" ", a.warnings()));
        }));
        return Ui.page(debts, Ui.row(debtName, debtBalance, debtApr, debtMin, add, remove),
                Ui.row(new Label("Extra per month:"), extra, compare), plans, warn,
                Ui.muted("Avalanche pays the highest interest rate first (least interest). "
                        + "Snowball pays the smallest balance first (quick wins)."));
    }

    private Node firePage() {
        invested.setText("10000");
        save.setText("15000");
        spend.setText("40000");
        age.setText("22");
        Button project = new Button("Project");
        project.setOnAction(e -> Ui.attempt(this::projectFire));
        scenarios.getColumns().add(Ui.column("Scenario", FireProjection.Scenario::label, 160));
        scenarios.getColumns().add(Ui.column("Save / yr", FireProjection.Scenario::yearlySaving, 110));
        scenarios.getColumns().add(Ui.column("Spend / yr", FireProjection.Scenario::annualSpending, 110));
        scenarios.getColumns().add(Ui.column("Target", FireProjection.Scenario::targetAmount, 130));
        scenarios.getColumns().add(Ui.column("Time", s -> s.reachable() ? s.yearsToFire() + " years" : "not reachable", 110));
        scenarios.setPrefHeight(150);
        fireChart.setLegendVisible(false);
        fireChart.setCreateSymbols(false);
        fireChart.setAnimated(false);
        fireChart.setPrefHeight(240);
        fireChart.getXAxis().setLabel("Years from now");
        fireChart.getYAxis().setLabel("Invested ($)");
        ScrollPane sp = new ScrollPane(Ui.page(
                Ui.row(invested, save, spend, new Label("Age"), age, project,
                        Ui.muted("5% real return, 4% withdrawal rate")),
                fireResult, scenarios, fireChart));
        sp.setFitToWidth(true);
        return sp;
    }

    private void projectFire() {
        int a;
        try {
            a = Integer.parseInt(age.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Age must be a whole number");
        }
        FireProjection p = app.projectFire(new FireInputs(Ui.money(invested, "the invested amount"),
                Ui.money(save, "yearly saving"), Ui.money(spend, "yearly spending"), 0.05, 0.04, a));
        fireResult.setText(p.reachable()
                ? "FIRE in " + p.yearsToFire() + " years (age " + p.fireAge() + "), target " + p.targetAmount()
                : "Not reachable within 70 years — target " + p.targetAmount());
        scenarios.getItems().setAll(p.scenarios());
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        for (int i = 0; i < p.yearlyBalances().size(); i++) {
            s.getData().add(new XYChart.Data<>(i, p.yearlyBalances().get(i).getAmount()));
        }
        fireChart.getData().setAll(List.of(s));
    }

    private static Integer intOrNull(TextField f) {
        String t = f.getText() == null ? "" : f.getText().trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(f.getPromptText() + " must be a year, e.g. 2004");
        }
    }

    private static void setIfEmpty(TextField f, Object value) {
        if ((f.getText() == null || f.getText().isBlank()) && value != null) {
            f.setText(value.toString());
        }
    }
}
