package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Debt;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.PayoffMethod;
import com.maplecfo.planning.PayoffPlan;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.List;

/** F08 debt payoff: list the debts, then compare avalanche and snowball. */
final class DebtView implements View {

    private final MapleCfoFacade app;
    private final VBox content = Ui.page();
    private final ScrollPane scroll = Ui.scroll(content);
    private final TableView<Debt> table = Ui.table("No debts.");
    private final TextField extra = Ui.field("e.g. 200");
    private final VBox plans = new VBox();

    DebtView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        table.getColumns().add(Ui.column("Debt", Debt::getName, 220));
        table.getColumns().add(Ui.moneyColumn("Owed", Debt::getBalance, 120, false));
        table.getColumns().add(Ui.column("Interest", d -> d.getApr() + "%", 90));
        table.getColumns().add(Ui.moneyColumn("Minimum / month", Debt::getMinPayment, 140, false));
        table.getColumns().add(Ui.nodeColumn("", d -> {
            Button rm = Ui.flat("Remove");
            rm.getStyleClass().add(Styles.DANGER);
            rm.setOnAction(e -> {
                if (Ui.confirm("Remove " + d.getName() + "?")) {
                    Ui.attempt(() -> app.removeDebt(d.getId()));
                    refresh();
                }
            });
            return rm;
        }, 100));
        Ui.fitRows(table, 10);
        extra.setText("0");
    }

    @Override public String id() { return Nav.DEBT; }
    @Override public String title() { return "Debt payoff"; }
    @Override public String subtitle() { return "Which debt to pay first, and when you'll be debt-free"; }
    @Override public String section() { return "Plan"; }
    @Override public String icon() { return Icons.CARD; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        content.getChildren().clear();
        Button add = Ui.primary("Add a debt", Icons.PLUS);
        add.setOnAction(e -> addDialog());
        List<Debt> debts = app.getDebts();
        if (debts.isEmpty()) {
            plans.getChildren().clear();
            content.getChildren().add(Ui.emptyState(Icons.CARD, "No debts — nice!",
                    "If you have a credit card balance, student loan or car loan, add it here. MapleCFO shows "
                            + "which one to pay first and how much interest you can save.", add));
            return;
        }
        table.getItems().setAll(debts);
        content.getChildren().add(Ui.card("Your debts", null, add, table));
        Button compare = Ui.primary("Compare payoff plans");
        compare.setOnAction(e -> compare());
        Ui.Form form = new Ui.Form().add("Extra you can pay each month, on top of the minimums ($)", extra,
                "Even a small extra amount makes a big difference.");
        form.grid.setMaxWidth(520);
        VBox planCard = Ui.card("Make a plan", "Compare the two common ways to pay off debt.", null, form.grid, compare, plans);
        content.getChildren().add(planCard);
        if (!plans.getChildren().isEmpty()) compare();
    }

    private void compare() {
        Ui.attempt(() -> {
            Money x = Ui.moneyOrZero(extra, "the extra payment");
            PayoffPlan a = app.planDebtPayoff(PayoffMethod.AVALANCHE, x);
            PayoffPlan s = app.planDebtPayoff(PayoffMethod.SNOWBALL, x);
            boolean avalancheBetter = !a.totalInterest().isGreaterThan(s.totalInterest());
            plans.getChildren().setAll(new ResponsiveGrid(300, 16,
                    planCard(a, "Highest interest first. Costs the least.", avalancheBetter, s),
                    planCard(s, "Smallest balance first. Quick wins keep you motivated.", !avalancheBetter, a)));
            if (!a.warnings().isEmpty()) plans.getChildren().add(Ui.banner(String.join(" ", a.warnings()), "warn"));
        });
    }

    private Node planCard(PayoffPlan p, String what, boolean best, PayoffPlan other) {
        Label name = new Label(p.strategyName());
        name.getStyleClass().add(Styles.TITLE_4);
        VBox card = Ui.card(Ui.row(name, Ui.spacer(), best ? Ui.chip("Saves the most", "ok") : new Label()),
                Ui.small(what));
        if (!p.completes()) {
            card.getChildren().add(Ui.banner("The payments don't cover the interest. Pay more each month.", "bad"));
            return card;
        }
        card.getChildren().addAll(
                new VBox(0, Ui.big(p.monthsToDebtFree() + " months"), Ui.muted("debt-free by " + p.debtFreeMonth())),
                Ui.small("Total interest " + p.totalInterest()
                        + (best && other.completes() && other.totalInterest().isGreaterThan(p.totalInterest())
                        ? " (" + other.totalInterest().minus(p.totalInterest()) + " less)" : "")),
                Ui.small("Order: " + String.join(" → ", p.payoffs().stream().map(PayoffPlan.DebtPayoff::debtName).toList())));
        return card;
    }

    private void addDialog() {
        TextField name = Ui.field("e.g. RBC Visa");
        TextField owed = Ui.field("e.g. 2,400");
        TextField apr = Ui.field("e.g. 20.99");
        TextField min = Ui.field("e.g. 60");
        Ui.Form form = new Ui.Form()
                .add("Name", name, null)
                .add("Amount owed ($)", owed, null)
                .add("Interest rate (% per year)", apr, "Shown on your statement as APR.")
                .add("Minimum monthly payment ($)", min, null);
        Ui.dialog("Add a debt", null, form, "Add debt", () -> {
            if (name.getText().isBlank()) throw new IllegalArgumentException("Give the debt a name.");
            double rate;
            try {
                rate = Double.parseDouble(apr.getText().trim().replace("%", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Interest rate must be a number, for example 20.99.");
            }
            app.addDebt(name.getText().trim(), Ui.money(owed, "the amount owed"), rate,
                    Ui.money(min, "the minimum payment"));
        });
        refresh();
    }
}
