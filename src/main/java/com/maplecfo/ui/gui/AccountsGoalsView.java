package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.SavingsGoal;
import com.maplecfo.planning.AffordabilityResult;
import com.maplecfo.planning.GoalForecast;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;

/** F03 accounts and net worth, F09 savings goals and F11 "Can I afford it?". */
final class AccountsGoalsView implements View {

    private final MapleCfoFacade app;
    private final ScrollPane root;

    private final Label netWorth = Ui.big("-");
    private final TableView<Account> accounts = Ui.table("Add your first account below.");
    private final TextField accName = Ui.field("Name, e.g. TD Chequing", 180);
    private final ComboBox<AccountType> accType = new ComboBox<>();
    private final TextField accBalance = Ui.field("Balance (debts: amount owed)", 190);
    private final TextField accBank = Ui.field("Bank", 100);
    private final TextField newBalance = Ui.field("New balance", 120);

    private final TableView<GoalForecast> goals = Ui.table("No goals yet.");
    private final TextField goalName = Ui.field("Goal, e.g. Emergency fund", 180);
    private final TextField goalTarget = Ui.field("Target", 100);
    private final DatePicker goalDeadline = new DatePicker();
    private final TextField deposit = Ui.field("Deposit", 100);
    private final Label goalForecast = Ui.muted("Select a goal to see its forecast.");

    private final TextField affordAmount = Ui.field("Amount, e.g. 1500", 140);
    private final DatePicker affordDate = new DatePicker();
    private final Label affordResult = Ui.muted("");

    AccountsGoalsView(MapleCfoFacade app) {
        this.app = app;

        // ---- accounts (F03)
        accType.getItems().setAll(AccountType.values());
        accType.setPromptText("Type");
        accounts.getColumns().add(Ui.column("Name", Account::getName, 180));
        accounts.getColumns().add(Ui.column("Type", Account::getType, 120));
        accounts.getColumns().add(Ui.column("Bank", Account::getInstitution, 100));
        accounts.getColumns().add(Ui.column("Balance", a -> (a.isLiability() ? "-" : "") + a.getBalance(), 120));
        accounts.setPrefHeight(200);
        Button add = new Button("Add account");
        add.setOnAction(e -> Ui.attempt(this::addAccount));
        Button update = new Button("Update balance");
        update.setOnAction(e -> Ui.attempt(() -> {
            Account a = selected(accounts.getSelectionModel().getSelectedItem(), "an account");
            app.updateAccountBalance(a.getId(), Ui.money(newBalance, "the new balance"));
            newBalance.clear();
        }));
        Button remove = new Button("Remove");
        remove.setOnAction(e -> Ui.attempt(() ->
                app.removeAccount(selected(accounts.getSelectionModel().getSelectedItem(), "an account").getId())));

        // ---- goals (F09)
        goalDeadline.setPromptText("Deadline");
        goals.getColumns().add(Ui.column("Goal", f -> f.goal().getName(), 160));
        goals.getColumns().add(Ui.column("Saved", f -> f.goal().getSaved() + " of " + f.goal().getTarget(), 180));
        goals.getColumns().add(Ui.column("Progress", f -> Math.round(f.goal().progress() * 100) + "%", 80));
        goals.getColumns().add(Ui.column("Deadline", f -> f.goal().getDeadline() == null ? "-" : f.goal().getDeadline(), 100));
        goals.getColumns().add(Ui.column("Needs / month", GoalForecast::requiredMonthly, 110));
        goals.getColumns().add(Ui.column("Status", f -> f.isComplete() ? "done" : f.onTrack() ? "on track" : "behind", 90));
        goals.setPrefHeight(170);
        goals.getSelectionModel().selectedItemProperty().addListener((o, a, f) ->
                goalForecast.setText(f == null ? "Select a goal to see its forecast." : f.summary()));
        Button addGoal = new Button("Add goal");
        addGoal.setOnAction(e -> Ui.attempt(() -> {
            String name = goalName.getText().trim();
            String id = Ui.uniqueId(name, app.getGoals().stream().map(SavingsGoal::getId).toList());
            app.createGoal(new SavingsGoal(id, name, Ui.money(goalTarget, "a target"),
                    null, goalDeadline.getValue(), null));
            goalName.clear();
            goalTarget.clear();
            goalDeadline.setValue(null);
        }));
        Button dep = new Button("Record deposit");
        dep.setOnAction(e -> Ui.attempt(() -> {
            GoalForecast f = selected(goals.getSelectionModel().getSelectedItem(), "a goal");
            app.recordGoalDeposit(f.goal().getId(), Ui.money(deposit, "the deposit"));
            deposit.clear();
        }));
        Button delGoal = new Button("Delete goal");
        delGoal.setOnAction(e -> Ui.attempt(() ->
                app.deleteGoal(selected(goals.getSelectionModel().getSelectedItem(), "a goal").goal().getId())));

        // ---- affordability (F11)
        affordDate.setValue(app.today().plusMonths(3));
        Button check = new Button("Can I afford it?");
        check.setOnAction(e -> Ui.attempt(() -> {
            AffordabilityResult r = app.checkAffordability(Ui.money(affordAmount, "an amount"), affordDate.getValue());
            affordResult.setText(r.verdict() + " — " + r.summary());
        }));

        root = new ScrollPane(Ui.page(
                Ui.card(Ui.heading("Net worth"), netWorth, accounts,
                        Ui.row(accName, accType, accBalance, accBank, add),
                        Ui.row(new Label("Selected account:"), newBalance, update, remove)),
                Ui.card(Ui.heading("Savings goals"), goals,
                        Ui.row(goalName, goalTarget, goalDeadline, addGoal),
                        Ui.row(new Label("Selected goal:"), deposit, dep, delGoal), goalForecast),
                Ui.card(Ui.heading("Can I afford it?"), Ui.row(affordAmount, new Label("by"), affordDate, check),
                        affordResult,
                        Ui.muted("Uses your chequing and savings balances, your average monthly surplus, "
                                + "goal contributions and a one-month safety buffer."))));
        root.setFitToWidth(true);
    }

    @Override public String title() { return "Accounts & Goals"; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        List<Account> list = app.getAccounts();
        accounts.getItems().setAll(list);
        netWorth.setText(list.isEmpty() ? "-" : app.getNetWorth().total().toString());
        goals.getItems().setAll(app.forecastGoals());
    }

    private void addAccount() {
        if (accType.getValue() == null) throw new IllegalArgumentException("Choose an account type");
        String name = accName.getText().trim();
        String id = Ui.uniqueId(name, app.getAccounts().stream().map(Account::getId).toList());
        List<String> warnings = app.addAccount(new Account(id, name, accType.getValue(),
                accBank.getText().isBlank() ? "-" : accBank.getText().trim(), Ui.money(accBalance, "a balance")));
        accName.clear();
        accBalance.clear();
        accBank.clear();
        if (!warnings.isEmpty()) Ui.info(String.join("\n", warnings));
    }

    private static <T> T selected(T item, String what) {
        if (item == null) throw new IllegalArgumentException("Select " + what + " first");
        return item;
    }
}
