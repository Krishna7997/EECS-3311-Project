package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.Account;
import com.maplecfo.domain.AccountType;
import com.maplecfo.domain.Money;
import com.maplecfo.planning.NetWorthSnapshot;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** F03 accounts and net worth: what you own minus what you owe, with history. */
final class AccountsView implements View {

    private final MapleCfoFacade app;
    private final VBox content = Ui.page();
    private final ScrollPane scroll = Ui.scroll(content);
    private final TableView<Account> table = Ui.table("No accounts yet.");
    private final LineChart<String, Number> chart = new LineChart<>(new CategoryAxis(), new NumberAxis());

    AccountsView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        table.getColumns().add(Ui.column("Account", Account::getName, 220));
        table.getColumns().add(Ui.column("Type", a -> Ui.pretty(a.getType()), 130));
        table.getColumns().add(Ui.column("Bank", Account::getInstitution, 110));
        table.getColumns().add(Ui.moneyColumn("Balance", a -> a.isLiability() ? a.getBalance().negate() : a.getBalance(),
                130, false));
        table.getColumns().add(Ui.nodeColumn("", this::actions, 200));
        Ui.fitRows(table, 12);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(240);
    }

    @Override public String id() { return Nav.ACCOUNTS; }
    @Override public String title() { return "Accounts & net worth"; }
    @Override public String subtitle() { return "Everything you own minus everything you owe"; }
    @Override public String section() { return "Money"; }
    @Override public String icon() { return Icons.WALLET; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        content.getChildren().clear();
        Button add = Ui.primary("Add account", Icons.PLUS);
        add.setOnAction(e -> addDialog());
        List<Account> accounts = app.getAccounts();
        if (accounts.isEmpty()) {
            content.getChildren().add(Ui.emptyState(Icons.WALLET, "Add your first account",
                    "Add each bank account, credit card, loan and investment account (TFSA, RRSP, FHSA) with its "
                            + "current balance. MapleCFO adds them up into your net worth.", add));
            return;
        }
        NetWorthSnapshot s = app.getNetWorth();
        content.getChildren().add(new ResponsiveGrid(220, 16,
                Ui.stat("Net worth", Ui.big(s.total().toString()), Ui.small("Assets minus debts")),
                Ui.stat("Assets", Ui.big(s.assets().toString()), Ui.small("Cash, savings and investments")),
                Ui.stat("Debts", Ui.big(s.liabilities().toString()), Ui.small("Credit cards and loans"))));
        table.getItems().setAll(accounts);
        content.getChildren().add(Ui.card("Your accounts", "Update a balance whenever it changes. Importing a CSV "
                + "with a balance column updates it for you.", add, table));

        Map<LocalDate, Money> history = app.getNetWorthHistory();
        if (history.size() > 1) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            history.forEach((d, v) -> series.getData().add(new XYChart.Data<>(d.toString(), v.getAmount())));
            chart.getData().setAll(List.of(series));
            content.getChildren().add(Ui.card("Net worth over time", "One point per day you changed something.",
                    null, chart));
        }
    }

    private Node actions(Account a) {
        Button update = Ui.flat("Update balance");
        update.setOnAction(e -> updateDialog(a));
        Button remove = Ui.flat("Remove");
        remove.getStyleClass().add(atlantafx.base.theme.Styles.DANGER);
        remove.setOnAction(e -> {
            if (Ui.confirm("Remove " + a.getName() + "? Its transactions stay.")) {
                Ui.attempt(() -> app.removeAccount(a.getId()));
                refresh();
            }
        });
        return new HBox(4, update, remove);
    }

    private void addDialog() {
        TextField name = Ui.field("e.g. TD Chequing");
        ComboBox<AccountType> type = new ComboBox<>();
        type.getItems().setAll(AccountType.values());
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(AccountType t) { return t == null ? "" : Ui.pretty(t); }
            @Override public AccountType fromString(String s) { return null; }
        });
        type.setPromptText("Choose a type");
        TextField balance = Ui.field("e.g. 2,500");
        TextField bank = Ui.field("e.g. TD (optional)");
        Ui.Form form = new Ui.Form()
                .add("Name", name, null)
                .add("Type", type, null)
                .add("Current balance ($)", balance, "For a credit card or loan, enter the amount you owe as a positive number.")
                .add("Bank", bank, null);
        Ui.dialog("Add an account", null, form, "Add account", () -> {
            if (name.getText().isBlank()) throw new IllegalArgumentException("Give the account a name.");
            if (type.getValue() == null) throw new IllegalArgumentException("Choose the account type.");
            String id = Ui.uniqueId(name.getText().trim(), app.getAccounts().stream().map(Account::getId).toList());
            List<String> warnings = app.addAccount(new Account(id, name.getText().trim(), type.getValue(),
                    bank.getText().isBlank() ? "—" : bank.getText().trim(), Ui.money(balance, "the current balance")));
            if (!warnings.isEmpty()) Ui.info(String.join("\n", warnings));
        });
        refresh();
    }

    private void updateDialog(Account a) {
        TextField balance = Ui.field("New balance");
        balance.setText(a.getBalance().getAmount().toPlainString());
        Ui.Form form = new Ui.Form().add("New balance ($)", balance,
                a.isLiability() ? "The amount you owe now, as a positive number." : null);
        Ui.dialog("Update " + a.getName(), null, form, "Save",
                () -> app.updateAccountBalance(a.getId(), Ui.money(balance, "the new balance")));
        refresh();
    }
}
