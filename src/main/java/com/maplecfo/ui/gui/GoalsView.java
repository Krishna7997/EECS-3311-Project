package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.planning.GoalForecast;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** F09 savings goals: one card per goal with progress and whether it is on track. */
final class GoalsView implements View {

    private final MapleCfoFacade app;
    private final VBox content = Ui.page();
    private final ScrollPane scroll = Ui.scroll(content);

    GoalsView(MapleCfoFacade app, Nav nav) {
        this.app = app;
    }

    @Override public String id() { return Nav.GOALS; }
    @Override public String title() { return "Savings goals"; }
    @Override public String subtitle() { return "Save for something and see if you will make it in time"; }
    @Override public String section() { return "Plan"; }
    @Override public String icon() { return Icons.FLAG; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        content.getChildren().clear();
        Button add = Ui.primary("New goal", Icons.PLUS);
        add.setOnAction(e -> newGoal());
        List<GoalForecast> goals = app.forecastGoals();
        if (goals.isEmpty()) {
            content.getChildren().add(Ui.emptyState(Icons.FLAG, "No savings goals yet",
                    "Pick something to save for, like a $5,000 emergency fund by next summer. MapleCFO works out "
                            + "how much to put aside each month and checks it against what you usually have left over.",
                    add));
            return;
        }
        content.getChildren().add(Ui.row(Ui.muted(goals.size() + (goals.size() == 1 ? " goal" : " goals")),
                Ui.spacer(), add));
        Node[] cards = goals.stream().map(this::goalCard).toArray(Node[]::new);
        content.getChildren().add(new ResponsiveGrid(360, 16, cards));
    }

    private Node goalCard(GoalForecast f) {
        SavingsGoal g = f.goal();
        Label name = new Label(g.getName());
        name.getStyleClass().add(Styles.TITLE_4);
        Label chip = f.isComplete() ? Ui.chip("Done", "info") : f.onTrack() ? Ui.chip("On track", "ok")
                : Ui.chip("Behind", "warn");
        HBox top = new HBox(10, name, Ui.spacer(), chip);
        top.setAlignment(Pos.CENTER_LEFT);
        Label amount = Ui.big(g.getSaved().toString());
        Label of = Ui.muted("of " + g.getTarget() + (g.getDeadline() != null ? " by " + g.getDeadline() : ""));
        ProgressBar bar = new ProgressBar(g.progress());
        bar.setMaxWidth(Double.MAX_VALUE);
        if (!f.onTrack() && !f.isComplete()) bar.getStyleClass().add("near");
        Label forecast = Ui.small(f.summary());
        Button deposit = Ui.secondary("Add money");
        deposit.setOnAction(e -> depositDialog(g));
        deposit.setDisable(f.isComplete());
        Button delete = Ui.flat("Delete");
        delete.getStyleClass().add(Styles.DANGER);
        delete.setOnAction(e -> {
            if (Ui.confirm("Delete the goal \"" + g.getName() + "\"?")) {
                Ui.attempt(() -> app.deleteGoal(g.getId()));
                refresh();
            }
        });
        VBox card = Ui.card(top, new VBox(0, amount, of), bar, forecast, Ui.row(deposit, delete));
        return card;
    }

    private void newGoal() {
        TextField name = Ui.field("e.g. Emergency fund");
        TextField target = Ui.field("e.g. 5,000");
        TextField saved = Ui.field("0");
        DatePicker deadline = new DatePicker();
        deadline.setPromptText("Optional");
        Ui.Form form = new Ui.Form()
                .add("What are you saving for?", name, null)
                .add("Target amount ($)", target, null)
                .add("Already saved ($)", saved, "Leave empty if you are starting from zero.")
                .add("By when?", deadline, "Optional. With a date, MapleCFO tells you how much to save each month.");
        Ui.dialog("New savings goal", null, form, "Create goal", () -> {
            if (name.getText().isBlank()) throw new IllegalArgumentException("Give the goal a name.");
            String id = Ui.uniqueId(name.getText().trim(), app.getGoals().stream().map(SavingsGoal::getId).toList());
            app.createGoal(new SavingsGoal(id, name.getText().trim(), Ui.money(target, "the target amount"),
                    Ui.moneyOrZero(saved, "the amount already saved"), deadline.getValue(), null));
        });
        refresh();
    }

    private void depositDialog(SavingsGoal g) {
        TextField amount = Ui.field("e.g. 200");
        Ui.Form form = new Ui.Form().add("Amount ($)", amount, "Money you have put aside for this goal.");
        Ui.dialog("Add money to " + g.getName(), null, form, "Add",
                () -> app.recordGoalDeposit(g.getId(), Ui.money(amount, "an amount")));
        refresh();
    }
}
