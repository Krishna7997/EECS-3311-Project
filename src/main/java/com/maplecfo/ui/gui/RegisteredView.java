package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.ContributionKind;
import com.maplecfo.domain.ContributionRecord;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.planning.ContributionCheck;
import com.maplecfo.planning.ContributionRoom;
import com.maplecfo.planning.RrspRoomEstimator;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

/** F06 TFSA / RRSP / FHSA room, the profile it needs, and F14's RRSP estimate. */
final class RegisteredView implements View {

    private final MapleCfoFacade app;
    private final VBox content = Ui.page();
    private final ScrollPane scroll = Ui.scroll(content);

    RegisteredView(MapleCfoFacade app, Nav nav) {
        this.app = app;
    }

    @Override public String id() { return Nav.REGISTERED; }
    @Override public String title() { return "TFSA · RRSP · FHSA"; }
    @Override public String subtitle() { return "How much you can still put in this year, so you never over-contribute"; }
    @Override public String section() { return "Plan"; }
    @Override public String icon() { return Icons.SHIELD; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        content.getChildren().clear();
        int year = app.today().getYear();
        Node[] cards = List.of(AccountType.TFSA, AccountType.RRSP, AccountType.FHSA).stream()
                .map(t -> roomCard(t, year)).toArray(Node[]::new);
        content.getChildren().add(new ResponsiveGrid(280, 16, cards));
        content.getChildren().add(profileCard());
    }

    private static String explain(AccountType t) {
        return switch (t) {
            case TFSA -> "Tax-Free Savings Account. Room adds up every year since you turned 18 in Canada.";
            case RRSP -> "Retirement savings. Your limit is on your CRA Notice of Assessment.";
            case FHSA -> "First Home Savings Account. $8,000 a year, $40,000 in total.";
            default -> "";
        };
    }

    private static String friendly(String field) {
        return switch (field) {
            case "birthYear" -> "your birth year";
            case "residentSinceYear" -> "the year you became a resident of Canada";
            case "rrspDeductionLimit" -> "your RRSP deduction limit";
            case "fhsaOpenedYear" -> "the year you opened your FHSA (only if you have one)";
            default -> field;
        };
    }

    private Node roomCard(AccountType t, int year) {
        Label name = new Label(t.name());
        name.getStyleClass().add(Styles.TITLE_3);
        List<String> missing = app.getProfile().missingFieldsFor(t);
        VBox card;
        if (!missing.isEmpty()) {
            card = Ui.card(Ui.row(name, Ui.spacer(), Ui.chip("Need details", "neutral")), Ui.small(explain(t)),
                    Ui.muted("To work this out, fill in " + missing.stream().map(RegisteredView::friendly)
                            .collect(Collectors.joining(" and ")) + " below."));
            return card;
        }
        ContributionRoom r = app.getContributionRoom(t, year);
        Label available = Ui.big(r.available().toString());
        Label chip = r.isOverContributed() ? Ui.chip("Over the limit", "bad")
                : r.isOverLimit() ? Ui.chip("In $2,000 allowance", "warn") : Ui.chip("Room left", "ok");
        Button record = Ui.primary("Contribution / withdrawal", Icons.PLUS);
        record.setOnAction(e -> recordDialog(t));
        Button history = Ui.flat("History");
        history.setOnAction(e -> historyPopup(t));
        Money[] totals = app.getContributionTotals(t, year);
        card = Ui.card(Ui.row(name, Ui.spacer(), chip), Ui.small(explain(t)),
                new VBox(0, available, Ui.muted("available in " + year)),
                Ui.small("Limit " + r.limit() + " · used " + r.used()),
                Ui.small("This year: put in " + totals[0] + " · taken out " + totals[1]), Ui.row(record, history));
        if (t == AccountType.RRSP) {
            for (com.maplecfo.planning.RepaymentPlan p : app.getRepaymentPlans()) {
                card.getChildren().add(Ui.banner(p.summary(year), p.dueThisYear().isPositive() ? "warn" : "info"));
                p.warnings().forEach(w -> card.getChildren().add(Ui.banner(w, "bad")));
            }
        }
        if (t == AccountType.TFSA && totals[1].isPositive() && !r.isOverLimit()) {
            card.getChildren().add(Ui.small("The " + totals[1] + " you took out comes back as room on January 1, "
                    + (year + 1) + "."));
        }
        if (r.isOverContributed()) {
            card.getChildren().add(Ui.banner("CRA charges 1% a month on the extra amount. Withdraw it soon.", "bad"));
        } else if (r.isOverLimit()) {
            card.getChildren().add(Ui.banner("Over your deduction limit but within the $2,000 CRA allows without tax. "
                    + "You can't deduct the extra, and don't add more.", "warn"));
        }
        return card;
    }

    /** Every contribution and withdrawal logged for this account type. */
    private void historyPopup(AccountType t) {
        javafx.scene.control.TableView<ContributionRecord> table = Ui.table("Nothing recorded yet.");
        table.getColumns().add(Ui.column("Date", ContributionRecord::date, 120));
        table.getColumns().add(Ui.column("What", r -> r.kind().label(), 220));
        table.getColumns().add(Ui.moneyColumn("Amount", r -> r.kind().isWithdrawal()
                ? r.amount().negate() : r.amount(), 140, false));
        table.getItems().setAll(app.getContributions(t));
        Ui.fitRows(table, 10);
        Money[] totals = app.getContributionTotals(t, app.today().getYear());
        Ui.view(t + " history", "This year you put in " + totals[0] + " and took out " + totals[1] + ".", table);
    }

    private Node profileCard() {
        UserProfile p = app.getProfile();
        TextField birth = Ui.field("e.g. 2003");
        TextField resident = Ui.field("e.g. 2010");
        TextField rrsp = Ui.field("e.g. 5,400");
        TextField fhsa = Ui.field("Leave empty if you don't have one");
        TextField pension = Ui.field("Leave empty if you have no workplace pension");
        if (p.getPensionAdjustment() != null) pension.setText(p.getPensionAdjustment().getAmount().toPlainString());
        if (p.getBirthYear() != null) birth.setText(p.getBirthYear().toString());
        if (p.getResidentSinceYear() != null) resident.setText(p.getResidentSinceYear().toString());
        if (p.getRrspDeductionLimit() != null) rrsp.setText(p.getRrspDeductionLimit().getAmount().toPlainString());
        if (p.getFhsaOpenedYear() != null) fhsa.setText(p.getFhsaOpenedYear().toString());

        Button estimate = Ui.flat("Don't know? Estimate it from my pay");
        Label estimateText = Ui.small("");
        estimate.setOnAction(e -> {
            RrspRoomEstimator.Estimate est = app.estimateRrspRoom(app.today().getYear());
            estimateText.setText(est.explanation());
            if (est.status() == RrspRoomEstimator.Status.ESTIMATED) {
                rrsp.setText(est.estimate().getAmount().toPlainString());
            }
        });
        VBox rrspBox = new VBox(4, rrsp, Ui.row(estimate), estimateText);

        Ui.Form form = new Ui.Form()
                .add("Birth year", birth, null)
                .add("Year you became a resident of Canada", resident, "If you were born here, use your birth year.")
                .add("RRSP deduction limit ($)", rrspBox, "Shown on your latest CRA Notice of Assessment.")
                .add("Year you opened your FHSA", fhsa, null)
                .add("Pension adjustment ($)", pension, "Box 52 of last year's T4, only if you have a workplace "
                        + "pension. Used when estimating RRSP room.");
        Button save = Ui.primary("Save details");
        Label saved = Ui.small("");
        save.setOnAction(e -> Ui.attempt(() -> {
            UserProfile u = app.getProfile();
            u.setBirthYear(Ui.year(birth, "birth year"));
            u.setResidentSinceYear(Ui.year(resident, "the year you became a resident"));
            u.setRrspDeductionLimit(rrsp.getText().isBlank() ? null : Ui.money(rrsp, "the RRSP deduction limit"));
            u.setFhsaOpenedYear(Ui.year(fhsa, "the FHSA year"));
            u.setPensionAdjustment(pension.getText().isBlank() ? null : Ui.money(pension, "the pension adjustment"));
            app.saveProfile(u);
            refresh();
        }));
        VBox card = Ui.card("Your details", "Used only to calculate your room. Stored on this computer.", null,
                form.grid, Ui.row(save, saved));
        card.setMaxWidth(620);
        return card;
    }

    /** Package-private so a smoke test can open it. */
    void recordDialog(AccountType t) {
        TextField amount = Ui.field("e.g. 500");
        DatePicker date = new DatePicker(app.today());
        javafx.scene.control.ComboBox<ContributionKind> kind = new javafx.scene.control.ComboBox<>();
        for (ContributionKind k : ContributionKind.values()) if (k.allowedFor(t)) kind.getItems().add(k);
        kind.setValue(ContributionKind.CONTRIBUTION);
        kind.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ContributionKind k) { return k == null ? "" : k.label(); }
            @Override public ContributionKind fromString(String s) { return null; }
        });
        // the room check happens while typing, so there is no separate "check" step
        javafx.scene.control.Label preview = new javafx.scene.control.Label();
        javafx.beans.property.SimpleStringProperty okText = new javafx.beans.property.SimpleStringProperty("Save");
        preview.setWrapText(true);
        preview.setMaxWidth(Double.MAX_VALUE);
        preview.managedProperty().bind(preview.visibleProperty());
        preview.setVisible(false);
        Runnable update = () -> {
            preview.getStyleClass().removeAll("room-ok", "room-over", "room-info", "room-warn");
            okText.set("Save");
            String text = amount.getText() == null ? "" : amount.getText().trim();
            if (text.isEmpty()) {
                preview.setVisible(false);
                return;
            }
            try {
                Money m = Ui.money(amount, "an amount");
                if (kind.getValue() != ContributionKind.CONTRIBUTION) {
                    preview.setText(explainKind(t, kind.getValue(), m));
                    preview.getStyleClass().add("room-info");
                } else {
                    ContributionCheck c = app.previewContribution(t, m);
                    switch (c.status()) {
                        case OK -> {
                            preview.setText("✓ Fits. You'll have " + c.roomAfter() + " of " + t + " room left this year.");
                            preview.getStyleClass().add("room-ok");
                        }
                        case ALLOWANCE -> {
                            preview.setText("⚠ " + c.excess() + " over your RRSP limit, but within the $2,000 CRA "
                                    + "allows without tax. You can't deduct the extra.");
                            preview.getStyleClass().add("room-warn");
                        }
                        case OVER -> {
                            preview.setText("⚠ That's " + c.excess() + " more than your room ("
                                    + c.room().available().max(Money.zero()) + "). CRA charges 1% a month on the extra. "
                                    + "Only save this if it already happened.");
                            preview.getStyleClass().add("room-over");
                            okText.set("Save anyway — it already happened");
                        }
                        case UNKNOWN -> {
                            preview.setText("Fill in your details on this page to check the room.");
                            preview.getStyleClass().add("room-info");
                        }
                    }
                }
                preview.setVisible(true);
            } catch (RuntimeException ex) {
                preview.setText(Ui.message(ex));
                preview.getStyleClass().add("room-info");
                preview.setVisible(true);
            }
        };
        amount.textProperty().addListener((o, a, b) -> update.run());
        kind.valueProperty().addListener((o, a, b) -> update.run());
        Ui.Form form = new Ui.Form()
                .add("What happened?", kind, null)
                .add("Amount ($)", amount, null)
                .note(preview)
                .add("Date", date, null);
        Ui.dialog("Add to your " + t + " history", "MapleCFO checks your room as you type.", form, okText, () -> {
            Money m = Ui.money(amount, "an amount");
            if (date.getValue() == null) throw new IllegalArgumentException("Choose the date.");
            app.recordContribution(new ContributionRecord(t, date.getValue(), m,
                    kind.getValue()));
        }, this::refresh);
    }

    /** What a withdrawal, repayment or transfer means, shown while the user types the amount. */
    private String explainKind(AccountType t, ContributionKind k, Money m) {
        int year = app.today().getYear();
        return switch (k) {
            case WITHDRAWAL -> switch (t) {
                case TFSA -> "Tax-free. Taking out " + m + " gives you that room back on January 1, " + (year + 1) + ".";
                case RRSP -> m + " is added to your income and taxed; the bank holds back some tax. RRSP room "
                        + "does not come back.";
                default -> "Taxed as income unless it is a qualifying withdrawal for your first home. FHSA room "
                        + "does not come back.";
            };
            case HBP_WITHDRAWAL -> "Tax-free if it is for your first home (up to $60,000). You repay it into your "
                    + "RRSP over 15 years; repayments don't use room.";
            case LLP_WITHDRAWAL -> "Tax-free for full-time school (up to $10,000 a year, $20,000 total). You repay it "
                    + "over 10 years.";
            case HBP_REPAYMENT, LLP_REPAYMENT -> "A repayment doesn't use RRSP room. Remember to designate it as a "
                    + "repayment on your tax return.";
            case TRANSFER_TO_RRSP -> "Tax-free move to your RRSP that doesn't use RRSP room. FHSA room does not come back.";
            case CONTRIBUTION -> "";
        };
    }
}
