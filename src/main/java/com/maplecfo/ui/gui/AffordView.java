package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.planning.AffordabilityResult;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** F11 "Can I afford it?": a two-question form and a clear answer with the numbers behind it. */
final class AffordView implements View {

    private final MapleCfoFacade app;
    private final TextField what = Ui.field("e.g. New laptop (optional)");
    private final TextField amount = Ui.field("e.g. 1,500");
    private final DatePicker date = new DatePicker();
    private final VBox result = new VBox();
    private final ScrollPane scroll;

    AffordView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        date.setValue(app.today().plusMonths(3));
        Button check = Ui.primary("Check");
        check.setOnAction(e -> check());
        amount.setOnAction(e -> check());
        Ui.Form form = new Ui.Form()
                .add("What do you want to buy?", what, null)
                .add("How much does it cost? ($)", amount, null)
                .add("When do you want to buy it?", date, null);
        VBox ask = Ui.card("Can I afford it?", "MapleCFO uses your cash, what you usually have left each month, "
                + "your savings goals and a one-month safety buffer.", null, form.grid, check);
        ask.setMaxWidth(560);
        scroll = Ui.scroll(Ui.page(ask, result));
    }

    @Override public String id() { return Nav.AFFORD; }
    @Override public String title() { return "Can I afford it?"; }
    @Override public String subtitle() { return "Check a purchase before you make it"; }
    @Override public String section() { return "Plan"; }
    @Override public String icon() { return Icons.CART; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        // the result stays until the user checks again
    }

    private void check() {
        Ui.attempt(() -> show(app.checkAffordability(Ui.money(amount, "the price"), date.getValue())));
    }

    private void show(AffordabilityResult r) {
        String item = what.getText().isBlank() ? r.amount().toString() : what.getText().trim() + " (" + r.amount() + ")";
        String head;
        String kind;
        switch (r.verdict()) {
            case YES -> { head = "Yes, you can afford " + item + "."; kind = "ok"; }
            case TIGHT -> { head = "It's tight: " + item + " would eat into your safety buffer."; kind = "warn"; }
            case NO -> { head = "Not yet: you'd be short by " + r.shortfall() + "."; kind = "bad"; }
            default -> { head = "Not enough information yet."; kind = "info"; }
        }
        Label h = new Label(head);
        h.getStyleClass().add(Styles.TITLE_3);
        h.setWrapText(true);
        VBox box = Ui.card(Ui.chip(switch (r.verdict()) {
            case YES -> "YES";
            case TIGHT -> "TIGHT";
            case NO -> "NOT YET";
            case INSUFFICIENT_DATA -> "NEED MORE DATA";
        }, kind), h);
        if (r.verdict() != AffordabilityResult.Verdict.INSUFFICIENT_DATA) {
            GridPane g = new GridPane();
            g.setHgap(24);
            g.setVgap(8);
            int row = 0;
            row = line(g, row, "Cash in chequing and savings today", r.currentCash().toString());
            if (r.forecast().registeredSavingsPerMonth().isPositive()) {
                row = line(g, row, "Moved to TFSA / RRSP / FHSA each month", r.forecast().registeredSavingsPerMonth().toString());
            }
            row = line(g, row, "Usually left over in chequing each month", r.forecast().cashSurplus().toString());
            row = line(g, row, "Expected cash by " + r.date() + " (after goal savings)", r.projectedCash().toString());
            row = line(g, row, "Safety buffer (one month of bills)", r.safetyBuffer().toString());
            row = line(g, row, "Price", r.amount().toString());
            line(g, row, r.headroom().isNegative() ? "Short by" : "Left over after buying", r.headroom().abs().toString());
            box.getChildren().add(g);
            if (!r.affectedGoals().isEmpty()) {
                box.getChildren().add(Ui.banner("It would fit if you paused these goals: "
                        + String.join(", ", r.affectedGoals()) + ".", "info"));
            }
        } else {
            box.getChildren().add(Ui.muted(r.summary()));
        }
        if (r.lowConfidence()) {
            box.getChildren().add(Ui.banner("Based on less than 2 months of transactions, so treat this as a rough "
                    + "answer.", "warn"));
        }
        box.setMaxWidth(560);
        result.getChildren().setAll(box);
    }

    private static int line(GridPane g, int row, String label, String value) {
        Label l = Ui.muted(label);
        Label v = new Label(value);
        v.getStyleClass().add(Styles.TEXT_BOLD);
        GridPane.setHalignment(v, javafx.geometry.HPos.RIGHT);
        g.add(l, 0, row);
        g.add(v, 1, row);
        return row + 1;
    }
}
