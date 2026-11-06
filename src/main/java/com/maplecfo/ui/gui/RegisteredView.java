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
        Label chip = r.isOverContributed() ? Ui.chip("Over the limit", "bad") : Ui.chip("Room left", "ok");
        Button check = Ui.secondary("Check an amount");
        check.setOnAction(e -> checkDialog(t));
        Button record = Ui.flat("Record a contribution");
        record.setOnAction(e -> recordDialog(t));
        card = Ui.card(Ui.row(name, Ui.spacer(), chip), Ui.small(explain(t)),
                new VBox(0, available, Ui.muted("available in " + year)),
                Ui.small("Limit " + r.limit() + " · used " + r.used()), Ui.row(check, record));
        if (r.isOverContributed()) {
            card.getChildren().add(Ui.banner("CRA charges 1% a month on the extra amount. Withdraw it soon.", "bad"));
        }
        return card;
    }

    private Node profileCard() {
        UserProfile p = app.getProfile();
        TextField birth = Ui.field("e.g. 2003");
        TextField resident = Ui.field("e.g. 2010");
        TextField rrsp = Ui.field("e.g. 5,400");
        TextField fhsa = Ui.field("Leave empty if you don't have one");
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
                .add("Year you opened your FHSA", fhsa, null);
        Button save = Ui.primary("Save details");
        Label saved = Ui.small("");
        save.setOnAction(e -> Ui.attempt(() -> {
            UserProfile u = app.getProfile();
            u.setBirthYear(Ui.year(birth, "birth year"));
            u.setResidentSinceYear(Ui.year(resident, "the year you became a resident"));
            u.setRrspDeductionLimit(rrsp.getText().isBlank() ? null : Ui.money(rrsp, "the RRSP deduction limit"));
            u.setFhsaOpenedYear(Ui.year(fhsa, "the FHSA year"));
            app.saveProfile(u);
            refresh();
        }));
        VBox card = Ui.card("Your details", "Used only to calculate your room. Stored on this computer.", null,
                form.grid, Ui.row(save, saved));
        card.setMaxWidth(620);
        return card;
    }

    private void checkDialog(AccountType t) {
        TextField amount = Ui.field("e.g. 3,000");
        Ui.Form form = new Ui.Form().add("Amount you plan to put in ($)", amount, null);
        String[] answer = new String[1];
        boolean ok = Ui.dialog("Check a " + t + " contribution", "Nothing is saved; this only checks the amount.",
                form, "Check", () -> {
                    ContributionCheck c = app.checkContribution(t, Ui.money(amount, "an amount"));
                    answer[0] = switch (c.status()) {
                        case OK -> "Yes, " + c.amount() + " fits. You would have " + c.roomAfter() + " left.";
                        case OVER -> "No: that is " + c.excess() + " more than your " + t + " room ("
                                + c.room().available() + "). CRA charges 1% a month on the extra.";
                        case UNKNOWN -> "Fill in your details first.";
                    };
                });
        if (ok) Ui.info(answer[0]);
        refresh();
    }

    private void recordDialog(AccountType t) {
        TextField amount = Ui.field("e.g. 500");
        DatePicker date = new DatePicker(app.today());
        CheckBox withdrawal = new CheckBox("This was a withdrawal");
        Ui.Form form = new Ui.Form()
                .add("Amount ($)", amount, null)
                .add("Date", date, null)
                .add("", withdrawal, "Withdrawals give the room back next year (TFSA).");
        Ui.dialog("Record a " + t + " contribution", null, form, "Record", () -> {
            Money m = Ui.money(amount, "an amount");
            if (date.getValue() == null) throw new IllegalArgumentException("Choose the date.");
            app.recordContribution(new ContributionRecord(t, date.getValue(), m,
                    withdrawal.isSelected() ? ContributionKind.WITHDRAWAL : ContributionKind.CONTRIBUTION));
        });
        refresh();
    }
}
