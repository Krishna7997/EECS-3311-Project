package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.RecurringCharge;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.List;

/** F05 subscriptions and bills found automatically in the transactions. */
final class SubscriptionsView implements View {

    private final MapleCfoFacade app;
    private javafx.scene.control.ScrollPane scroll;
    private final Nav nav;
    private final VBox content = Ui.page();
    private final TableView<RecurringCharge> table = Ui.table("No recurring charges found.");

    SubscriptionsView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        this.nav = nav;
        table.getColumns().add(Ui.column("Merchant", RecurringCharge::merchant, 260));
        table.getColumns().add(Ui.moneyColumn("Usual amount", RecurringCharge::averageAmount, 120, false));
        table.getColumns().add(Ui.column("How often", c -> Ui.pretty(c.frequency()), 100));
        table.getColumns().add(Ui.column("Next charge", RecurringCharge::nextExpectedDate, 120));
        table.getColumns().add(Ui.moneyColumn("Per year", RecurringCharge::yearlyCost, 120, false));
        table.getColumns().add(Ui.nodeColumn("", c -> c.priceIncreased() ? Ui.chip("Price went up", "bad")
                : c.variableAmount() ? Ui.chip("Amount varies", "neutral") : new Label(), 140));
        Ui.fitRows(table, 14);
        scroll = Ui.scroll(content);
    }

    @Override public String id() { return Nav.SUBSCRIPTIONS; }
    @Override public String title() { return "Subscriptions & bills"; }
    @Override public String subtitle() { return "Charges that repeat, found automatically in your transactions"; }
    @Override public String section() { return "Money"; }
    @Override public String icon() { return Icons.REPEAT; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        content.getChildren().clear();
        List<RecurringCharge> charges = app.getRecurringCharges();
        if (charges.isEmpty()) {
            Button go = Ui.primary("Import transactions", Icons.UPLOAD);
            go.setOnAction(e -> nav.go(Nav.TRANSACTIONS));
            content.getChildren().add(Ui.emptyState(Icons.REPEAT, "No subscriptions found yet",
                    "MapleCFO looks for charges that repeat every week, month or year. Import at least two or "
                            + "three months of transactions so it can spot them.", go));
            return;
        }
        Money year = charges.stream().map(RecurringCharge::yearlyCost).reduce(Money.zero(), Money::plus);
        long up = charges.stream().filter(RecurringCharge::priceIncreased).count();
        content.getChildren().add(new ResponsiveGrid(220, 16,
                Ui.stat("Per month", Ui.big(year.dividedBy(12).toString()), Ui.small("All repeating charges")),
                Ui.stat("Per year", Ui.big(year.toString()), Ui.small(charges.size() + " charges")),
                Ui.stat("Price increases", Ui.big(String.valueOf(up)), Ui.small(up == 0 ? "None found"
                        : "Check if you still need them"))));
        if (app.isRecurringLowConfidence()) {
            content.getChildren().add(Ui.banner("Based on less than 3 months of data, so some charges may be "
                    + "missing. Import more months for better results.", "warn"));
        }
        table.getItems().setAll(charges);
        content.getChildren().add(Ui.card("Repeating charges", "Biggest yearly cost first. Rent and bills are included.",
                null, table));
    }
}
