package com.maplecfo.ui.gui;

import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.domain.Transaction;
import com.maplecfo.domain.UserProfile;
import com.maplecfo.events.Alert;
import com.maplecfo.planning.GoalForecast;
import com.maplecfo.planning.NetWorthSnapshot;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Home: getting-started steps, this month's numbers, things that need attention and a quick question box. */
final class HomeView implements View {

    private final MapleCfoFacade app;
    private final Nav nav;
    private final VBox content = Ui.page();
    private final javafx.scene.control.ScrollPane root = Ui.scroll(content);

    HomeView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        this.nav = nav;
    }

    @Override public String id() { return Nav.HOME; }
    @Override public String title() { return "Home"; }
    @Override public String subtitle() { return "Your money at a glance and what to do next"; }
    @Override public String section() { return ""; }
    @Override public String icon() { return Icons.HOME; }
    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        content.getChildren().clear();
        content.getChildren().add(askBox());
        if (app.isMonthlyCheckinDue()) content.getChildren().add(monthlyCheckin());
        Node steps = gettingStarted();
        if (steps != null) content.getChildren().add(steps);
        content.getChildren().add(tiles());
        Node attention = attention();
        if (attention != null) content.getChildren().add(attention);
        content.getChildren().add(new ResponsiveGrid(380, 16, budgets(), goals()));
        Node alerts = recentAlerts();
        if (alerts != null) content.getChildren().add(alerts);
    }

    // ------------------------------------------------------------------ sections

    private Node askBox() {
        TextField q = Ui.field("Ask MapleCFO anything, e.g. \"Can I afford a $1,200 laptop by December?\"");
        q.getStyleClass().add("ask-field");
        HBox.setHgrow(q, Priority.ALWAYS);
        Button ask = Ui.primary("Ask", Icons.SPARK);
        Runnable go = () -> {
            if (!q.getText().isBlank()) nav.ask(q.getText().trim());
        };
        ask.setOnAction(e -> go.run());
        q.setOnAction(e -> go.run());
        HBox box = new HBox(10, q, ask);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().addAll("card", "ask-box");
        box.setPadding(new javafx.geometry.Insets(12));
        return box;
    }

    /** Once a month: the few things that keep MapleCFO's numbers right. */
    private Node monthlyCheckin() {
        YearMonth now = YearMonth.from(app.today());
        String month = now.getMonth().getDisplayName(TextStyle.FULL, Locale.CANADA);
        String last = now.minusMonths(1).getMonth().getDisplayName(TextStyle.FULL, Locale.CANADA);
        VBox list = new VBox(0,
                checkRow("1", "Import " + last + "'s bank CSVs", "One file per bank account and card. Or save them in "
                        + "your import folder: " + app.getImportFolder().getFileName() + ".", "Import", Nav.TRANSACTIONS),
                checkRow("2", "Update balances that aren't imported", "Investments, TFSA, RRSP, FHSA and loans.",
                        "Accounts", Nav.ACCOUNTS),
                checkRow("3", "Review last month and plan budgets", "On Monthly report, press AI review: it "
                        + "proposes next month's budgets for you to confirm.", "Review", Nav.REPORT),
                checkRow("4", "Check your TFSA, RRSP and FHSA room", "Confirm any contributions MapleCFO found.",
                        "Room", Nav.REGISTERED));
        Button done = Ui.primary("Mark as done", Icons.CHECK);
        done.setOnAction(e -> { app.completeMonthlyCheckin(); refresh(); });
        VBox card = Ui.card(month + " check-in", "A 5-minute routine at the start of each month keeps your numbers "
                + "right.", done, list);
        card.getStyleClass().add("checkin-card");
        return card;
    }

    private Node checkRow(String n, String title, String text, String button, String page) {
        Label badge = new Label(n);
        badge.getStyleClass().addAll("step-badge", "step-todo");
        Label t = new Label(title);
        t.getStyleClass().add("step-title");
        VBox words = new VBox(2, t, Ui.small(text));
        HBox.setHgrow(words, Priority.ALWAYS);
        Button b = Ui.secondary(button);
        b.setOnAction(e -> nav.go(page));
        HBox line = new HBox(14, badge, words, b);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("step-row");
        return line;
    }

    /** A checklist for new users; hidden once every step is done. */
    private Node gettingStarted() {
        YearMonth m = YearMonth.from(app.today());
        UserProfile p = app.getProfile();
        boolean imported = !app.searchTransactions("").isEmpty();
        boolean accounts = !app.getAccounts().isEmpty();
        boolean profile = p.getBirthYear() != null && p.getResidentSinceYear() != null;
        boolean budget = !app.getBudgetStatus(m).isEmpty();
        boolean goal = !app.getGoals().isEmpty();
        boolean asked = ChatView.hasAsked();
        boolean[] done = {imported, accounts, profile, budget, goal, asked};
        int count = 0;
        for (boolean d : done) if (d) count++;
        if (count == done.length) return null;

        VBox list = new VBox(0);
        list.getChildren().addAll(
                step(1, imported, "Import a bank CSV", "Download a CSV from your bank's website and import it. "
                        + "The format is detected for you.", "Import", Nav.TRANSACTIONS),
                step(2, accounts, "Add your accounts", "Chequing, savings, TFSA, credit cards… so MapleCFO knows "
                        + "your net worth.", "Add accounts", Nav.ACCOUNTS),
                step(3, profile, "Tell us about you", "Birth year and the year you came to Canada. Needed to work out "
                        + "your TFSA, RRSP and FHSA room.", "Fill in", Nav.REGISTERED),
                step(4, budget, "Set a budget", "Pick a category you overspend on, like dining, and set a "
                        + "monthly limit.", "Set budget", Nav.BUDGETS),
                step(5, goal, "Create a savings goal", "An emergency fund, a trip, a car… MapleCFO tells you if "
                        + "you are on track.", "New goal", Nav.GOALS),
                step(6, asked, "Ask MapleCFO a question", "Try \"How much did I spend on dining last month?\"",
                        "Ask", Nav.CHAT));
        ProgressBar progress = new ProgressBar(count / (double) done.length);
        progress.setPrefWidth(160);
        HBox head = Ui.row(Ui.small(count + " of " + done.length + " done"), progress);
        return Ui.card("Getting started", "Six quick steps to set up MapleCFO.", head, list);
    }

    private Node step(int n, boolean done, String title, String text, String button, String page) {
        Label badge = new Label(done ? "" : String.valueOf(n));
        badge.getStyleClass().addAll("step-badge", done ? "step-done" : "step-todo");
        if (done) badge.setGraphic(Icons.of(Icons.CHECK, 12));
        Label t = new Label(title);
        t.getStyleClass().add(done ? "step-title-done" : "step-title");
        VBox words = new VBox(2, t, Ui.small(text));
        HBox.setHgrow(words, Priority.ALWAYS);
        HBox line = new HBox(14, badge, words);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("step-row");
        if (!done) {
            Button b = Ui.secondary(button);
            b.setOnAction(e -> nav.go(page));
            line.getChildren().add(b);
        }
        return line;
    }

    private Node tiles() {
        java.time.LocalDate today = app.today();
        YearMonth m = YearMonth.from(today);
        String monthName = m.getMonth().getDisplayName(TextStyle.FULL, Locale.CANADA);
        com.maplecfo.reports.PeriodSummary month = app.summarize(m.atDay(1), today);
        com.maplecfo.reports.PeriodSummary year = app.summarize(today.withDayOfYear(1), today);
        Label nw;
        Label nwNote;
        if (app.getAccounts().isEmpty()) {
            nw = Ui.big("—");
            nwNote = Ui.small("Add your accounts to see it");
        } else {
            NetWorthSnapshot s = app.getNetWorth();
            nw = Ui.big(s.total().toString());
            nwNote = Ui.small("Assets " + s.assets() + " · Debts " + s.liabilities());
        }
        return new ResponsiveGrid(220, 16,
                Ui.stat("Net worth", nw, nwNote),
                Ui.stat("Spent in " + monthName, Ui.big(month.spending().toString()),
                        Ui.small("Income " + month.income() + " · transfers not counted")),
                savedTile("Saved in " + monthName, month, "No income yet in " + monthName),
                savedTile("Saved in " + today.getYear(), year, "No income recorded this year"));
    }

    /** Income minus spending, coloured, with the savings rate and what went to registered accounts. */
    private static Node savedTile(String caption, com.maplecfo.reports.PeriodSummary p, String noIncome) {
        Label value = Ui.big(p.saved().toString());
        value.getStyleClass().add(p.saved().isNegative() ? "negative" : "positive");
        String note = p.income().isPositive() ? p.savingsRatePercent() + "% of income" : noIncome;
        if (p.toRegistered().isPositive()) note += " · " + p.toRegistered() + " to TFSA/RRSP/FHSA";
        if (p.fromRegistered().isPositive()) note += " · " + p.fromRegistered() + " taken out";
        return Ui.stat(caption, value, Ui.small(note));
    }

    /** Things the user should act on: AI or detector proposals, transactions to sort, budgets over the limit. */
    private Node attention() {
        List<Node> rows = new ArrayList<>();
        for (Map.Entry<String, String> e : app.getPendingActions().entrySet()) {
            String id = e.getKey();
            Button ok = Ui.primary("Confirm");
            ok.setOnAction(ev -> Ui.attempt(() -> { app.confirmProposedAction(id); refresh(); }));
            Button no = Ui.secondary("Dismiss");
            no.setOnAction(ev -> Ui.attempt(() -> { app.dismissProposedAction(id); refresh(); }));
            rows.add(attentionRow("Waiting for your OK", e.getValue(), "info", ok, no));
        }
        int review = app.getTransactionsNeedingReview().size();
        if (review > 0) {
            Button b = Ui.secondary("Review");
            b.setOnAction(ev -> nav.go(Nav.TRANSACTIONS));
            rows.add(attentionRow("To sort", review + (review == 1 ? " transaction needs" : " transactions need")
                    + " a category", "warn", b));
        }
        for (BudgetStatus s : app.getBudgetStatus(YearMonth.from(app.today()))) {
            if (s.isExceeded()) {
                Button b = Ui.secondary("See budget");
                b.setOnAction(ev -> nav.go(Nav.BUDGETS));
                rows.add(attentionRow("Over budget", Ui.pretty(s.budget().category()) + ": " + s.spent() + " of "
                        + s.budget().limit(), "bad", b));
            }
        }
        if (rows.isEmpty()) return null;
        VBox list = new VBox(0);
        list.getChildren().addAll(rows);
        return Ui.card("Needs your attention", "Nothing changes until you confirm it.", null, list);
    }

    private Node attentionRow(String kind, String text, String chipKind, Node... actions) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(l, Priority.ALWAYS);
        HBox row = new HBox(12, Ui.chip(kind, chipKind), l);
        row.getChildren().addAll(actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }

    private Node budgets() {
        YearMonth m = YearMonth.from(app.today());
        List<BudgetStatus> list = app.getBudgetStatus(m);
        Button all = Ui.flat("See all");
        all.setOnAction(e -> nav.go(Nav.BUDGETS));
        VBox body = new VBox(10);
        if (list.isEmpty()) {
            Button set = Ui.secondary("Set a budget");
            set.setOnAction(e -> nav.go(Nav.BUDGETS));
            body.getChildren().addAll(Ui.muted("No budgets this month yet."), set);
        }
        for (BudgetStatus b : list) {
            body.getChildren().add(barRow(Ui.pretty(b.budget().category()), Math.min(1, b.percentUsed() / 100),
                    b.isExceeded() ? "over" : b.isNearLimit() ? "near" : "",
                    b.spent() + " of " + b.budget().limit()));
        }
        return Ui.card("Budgets this month", null, all, body);
    }

    private Node goals() {
        List<GoalForecast> list = app.forecastGoals();
        Button all = Ui.flat("See all");
        all.setOnAction(e -> nav.go(Nav.GOALS));
        VBox body = new VBox(10);
        if (list.isEmpty()) {
            Button add = Ui.secondary("Create a goal");
            add.setOnAction(e -> nav.go(Nav.GOALS));
            body.getChildren().addAll(Ui.muted("No savings goals yet."), add);
        }
        for (GoalForecast f : list) {
            HBox r = barRow(f.goal().getName(), f.goal().progress(), f.onTrack() || f.isComplete() ? "" : "near",
                    f.goal().getSaved() + " of " + f.goal().getTarget());
            r.getChildren().add(f.isComplete() ? Ui.chip("Done", "info")
                    : f.onTrack() ? Ui.chip("On track", "ok") : Ui.chip("Behind", "warn"));
            body.getChildren().add(r);
        }
        return Ui.card("Savings goals", null, all, body);
    }

    private static HBox barRow(String name, double progress, String style, String amount) {
        Label n = new Label(name);
        n.setMinWidth(110);
        n.setPrefWidth(130);
        ProgressBar bar = new ProgressBar(progress);
        bar.setMinWidth(60);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);
        if (!style.isEmpty()) bar.getStyleClass().add(style);
        Label a = Ui.small(amount);
        a.setWrapText(false);
        a.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(10, n, bar, a);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node recentAlerts() {
        List<Alert> all = app.getAlerts().stream()
                .filter(a -> a.source() != com.maplecfo.events.EventType.CONTRIBUTION_DETECTED).toList();
        if (all.isEmpty()) return null;
        VBox list = new VBox(0);
        for (int i = all.size() - 1; i >= Math.max(0, all.size() - 6); i--) {
            Alert a = all.get(i);
            String kind = switch (a.severity()) {
                case CRITICAL -> "bad";
                case WARNING -> "warn";
                case INFO -> "info";
            };
            Label l = new Label(a.message());
            l.setWrapText(true);
            HBox.setHgrow(l, Priority.ALWAYS);
            HBox row = new HBox(12, Ui.chip(Ui.pretty(a.severity()), kind), l);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("list-row");
            list.getChildren().add(row);
        }
        return Ui.card("Recent alerts", "Budget warnings, contribution checks, imports and reminders.", null, list);
    }
}
