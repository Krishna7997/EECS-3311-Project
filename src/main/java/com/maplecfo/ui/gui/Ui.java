package com.maplecfo.ui.gui;

import atlantafx.base.theme.Styles;
import com.maplecfo.domain.Money;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.function.Function;

/** Shared building blocks so every page looks and behaves the same way. */
final class Ui {

    private Ui() {
    }

    // ------------------------------------------------------------------ text

    static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().addAll(Styles.TITLE_4, "card-title");
        l.setWrapText(true);
        return l;
    }

    static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(Styles.TEXT_MUTED);
        l.setWrapText(true);
        return l;
    }

    static Label small(String text) {
        Label l = new Label(text);
        l.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
        l.setWrapText(true);
        return l;
    }

    static Label big(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("big-number");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /** "CREDIT_CARD" -> "Credit card": enum names in a friendlier form for the screen. */
    static String pretty(Object value) {
        String s = String.valueOf(value).replace('_', ' ').toLowerCase();
        if (s.equals("tfsa") || s.equals("rrsp") || s.equals("fhsa")) return s.toUpperCase();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ buttons and chips

    static Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(Styles.ACCENT);
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    static Button primary(String text, String icon) {
        Button b = primary(text);
        b.setGraphic(Icons.of(icon, 14));
        return b;
    }

    static Button secondary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(Styles.BUTTON_OUTLINED);
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    static Button flat(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    /** A small rounded status label: kind is ok, warn, bad, info or neutral. */
    static Label chip(String text, String kind) {
        Label l = new Label(text);
        l.getStyleClass().addAll("chip", "chip-" + kind);
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    // ------------------------------------------------------------------ layout

    static VBox page(Node... children) {
        VBox box = new VBox(16, children);
        box.setPadding(new Insets(4, 28, 28, 28));
        box.getStyleClass().add("page");
        box.setFillWidth(true);
        return box;
    }

    static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("page-scroll");
        return sp;
    }

    static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(20));
        return box;
    }

    /** A card with a title, an optional one-line explanation and actions on the right. */
    static VBox card(String title, String help, Node action, Node... body) {
        VBox head = new VBox(2, title(title));
        if (help != null && !help.isBlank()) head.getChildren().add(small(help));
        HBox top = new HBox(12, head, spacer());
        top.setAlignment(Pos.TOP_LEFT);
        if (action != null) top.getChildren().add(action);
        HBox.setHgrow(head, Priority.SOMETIMES);
        VBox box = card(top);
        box.getChildren().addAll(body);
        return box;
    }

    static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    static HBox row(Node... children) {
        HBox box = new HBox(10, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** A KPI tile: small caption, big value, optional note underneath. */
    static VBox stat(String caption, Label value, Label note) {
        Label c = new Label(caption.toUpperCase());
        c.getStyleClass().add("stat-caption");
        value.getStyleClass().add("stat-value");
        value.setMinWidth(Region.USE_PREF_SIZE);
        VBox box = new VBox(4, c, value);
        if (note != null) {
            note.setWrapText(true);
            box.getChildren().add(note);
        }
        box.getStyleClass().addAll("card", "stat");
        box.setPadding(new Insets(16, 18, 16, 18));
        return box;
    }

    /** What a page shows before there is any data: what it is for and the one thing to do next. */
    static VBox emptyState(String icon, String title, String text, Node... actions) {
        Region i = Icons.of(icon, 34);
        i.getStyleClass().add("empty-icon");
        Label t = new Label(title);
        t.getStyleClass().add(Styles.TITLE_3);
        Label d = muted(text);
        d.setMaxWidth(460);
        d.setAlignment(Pos.CENTER);
        d.setStyle("-fx-text-alignment: center;");
        HBox buttons = new HBox(10, actions);
        buttons.setAlignment(Pos.CENTER);
        VBox box = new VBox(10, i, t, d, buttons);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40, 20, 40, 20));
        box.getStyleClass().addAll("card", "empty-state");
        return box;
    }

    /** A coloured banner with a short message (kind: info, ok, warn, bad). */
    static HBox banner(String text, String kind, Node... actions) {
        Label l = new Label(text);
        l.setWrapText(true);
        HBox.setHgrow(l, Priority.ALWAYS);
        l.setMaxWidth(Double.MAX_VALUE);
        HBox box = new HBox(12, l);
        box.getChildren().addAll(actions);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().addAll("banner", "banner-" + kind);
        box.setPadding(new Insets(12, 16, 12, 16));
        return box;
    }

    // ------------------------------------------------------------------ forms and dialogs

    /** Label above control, with an optional help line, one field per row. Easy to scan and to fill in. */
    static final class Form {
        final GridPane grid = new GridPane();
        private int row;

        Form() {
            grid.setHgap(16);
            grid.setVgap(4);
            ColumnConstraints c = new ColumnConstraints();
            c.setHgrow(Priority.ALWAYS);
            c.setFillWidth(true);
            grid.getColumnConstraints().add(c);
        }

        /** A full-width line without a label, e.g. a live check under a field. */
        Form note(Node node) {
            if (node instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
            grid.add(node, 0, row++);
            Region gap = new Region();
            gap.setMinHeight(8);
            grid.add(gap, 0, row++);
            return this;
        }

        Form add(String label, Node control, String help) {
            Label l = new Label(label);
            l.getStyleClass().add("form-label");
            if (control instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
            grid.add(l, 0, row++);
            grid.add(control, 0, row++);
            if (help != null && !help.isBlank()) {
                grid.add(small(help), 0, row++);
            }
            Region gap = new Region();
            gap.setMinHeight(8);
            grid.add(gap, 0, row++);
            return this;
        }
    }

    /** An action that may reject the input with an exception whose message is shown to the user. */
    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    /** Like {@link Action} but returns a message to show in the pop-up (used for "check" pop-ups). */
    @FunctionalInterface
    interface Check {
        String run() throws Exception;
    }

    /** The in-window overlay used for pop-ups (set by the main window). */
    private static atlantafx.base.controls.ModalPane modal;

    static void useModalPane(atlantafx.base.controls.ModalPane pane) {
        modal = pane;
    }

    /**
     * Shows a pop-up form inside the main window (no separate window). OK runs the action; if it fails,
     * the message is shown in the pop-up and it stays open so the user can fix the input.
     * {@code onDone} runs after a successful OK.
     */
    static void dialog(String title, String intro, Form form, String okText, Action action, Runnable onDone) {
        dialog(title, intro, form, new javafx.beans.property.SimpleStringProperty(okText), action, onDone);
    }

    /** Same, with an OK label that can change while the user types (e.g. "Save anyway"). */
    static void dialog(String title, String intro, Form form, javafx.beans.value.ObservableValue<String> okText,
                       Action action, Runnable onDone) {
        popup(title, intro, form.grid, okText, popupError -> {
            action.run();
            return null;
        }, onDone, false, true);
    }

    /** A read-only pop-up (lists, history) with a single Close button. */
    static void view(String title, String intro, Node body) {
        popup(title, intro, body, new javafx.beans.property.SimpleStringProperty("Close"), e -> null, null, false, false);
    }

    /** A pop-up that shows its answer inside itself and stays open, e.g. "Check an amount". */
    static void check(String title, String intro, Form form, String okText, Check check) {
        popup(title, intro, form.grid, new javafx.beans.property.SimpleStringProperty(okText), popupError -> check.run(),
                null, true, true);
    }

    private interface PopupAction {
        String run(Label error) throws Exception;
    }

    private static void popup(String title, String intro, Node body, javafx.beans.value.ObservableValue<String> okText,
                              PopupAction action, Runnable onDone, boolean stayOpen, boolean showCancel) {
        Label heading = new Label(title);
        heading.getStyleClass().add(Styles.TITLE_3);
        heading.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        error.managedProperty().bind(error.visibleProperty());
        error.setVisible(false);
        Label answer = new Label();
        answer.getStyleClass().add("form-answer");
        answer.setWrapText(true);
        answer.setMaxWidth(Double.MAX_VALUE);
        answer.managedProperty().bind(answer.visibleProperty());
        answer.setVisible(false);

        Button cancel = secondary(stayOpen ? "Close" : "Cancel");
        Button ok = primary("");
        ok.textProperty().bind(okText);
        ok.setDefaultButton(true);
        HBox buttons = showCancel ? new HBox(10, spacer(), cancel, ok) : new HBox(10, spacer(), ok);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(14, heading);
        if (intro != null && !intro.isBlank()) card.getChildren().add(muted(intro));
        card.getChildren().addAll(body, error, answer, buttons);
        card.getStyleClass().addAll("card", "popup");
        card.setPadding(new Insets(24, 26, 20, 26));
        double w = body instanceof TableView<?> || body.lookup(".table-view") != null ? 760 : 480;
        card.setMaxWidth(w);
        card.setPrefWidth(w);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        Runnable close = () -> {
            if (modal != null) modal.hide(true);
        };
        cancel.setOnAction(e -> close.run());
        card.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) close.run();
        });
        ok.setOnAction(e -> {
            try {
                error.setVisible(false);
                String msg = action.run(error);
                if (stayOpen) {
                    answer.setText(msg);
                    answer.setVisible(true);
                } else {
                    close.run();
                    if (onDone != null) onDone.run();
                }
            } catch (Exception ex) {
                answer.setVisible(false);
                error.setText(message(ex));
                error.setVisible(true);
            }
        });

        if (modal == null) {
            // no main window (e.g. a smoke test): fall back to a normal dialog window
            Dialog<ButtonType> d = new Dialog<>();
            d.setTitle(title);
            d.getDialogPane().setContent(card);
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            d.getDialogPane().getStylesheets().add(MapleCfoApp.stylesheet());
            cancel.setOnAction(e -> d.close());
            d.show();
            return;
        }
        modal.show(card);
        javafx.application.Platform.runLater(() -> {
            Node first = body.lookup(".text-field");
            (first != null ? first : ok).requestFocus();
        });
    }

    /** A short message in a pop-up with one OK button. */
    static void info(String msg) {
        popup("MapleCFO", null, muted(msg), new javafx.beans.property.SimpleStringProperty("OK"), e -> null, null,
                false, false);
    }

    static void error(Throwable e) {
        Label l = new Label(message(e));
        l.setWrapText(true);
        l.getStyleClass().add("form-error");
        popup("Something needs your attention", null, l, new javafx.beans.property.SimpleStringProperty("OK"),
                x -> null, null, false, false);
    }

    /** Asks before doing something that cannot be undone; runs onYes only if the user agrees. */
    static void confirm(String msg, String okText, Runnable onYes) {
        popup("Are you sure?", null, muted(msg), new javafx.beans.property.SimpleStringProperty(okText), e -> null,
                onYes, false, true);
    }

    /** Runs an action and shows any error in a pop-up instead of crashing. */
    static void attempt(Action action) {
        try {
            action.run();
        } catch (Exception e) {
            error(e);
        }
    }

    /** Makes an id like "td-chequing" from a name, adding -2, -3... if it is already used. */
    static String uniqueId(String name, Collection<String> taken) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "item";
        String id = base;
        for (int i = 2; taken.contains(id); i++) id = base + "-" + i;
        return id;
    }

    static TextField field(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        return f;
    }

    /** Reads a dollar amount such as "1,250.50" or "$80". */
    static Money money(TextField f, String what) {
        String text = f.getText() == null ? "" : f.getText().trim().replace("$", "").replace(",", "");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Enter " + what + ".");
        }
        try {
            return Money.of(new BigDecimal(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(capital(what) + " must be a number, for example 250 or 1,250.50.");
        }
    }

    /** Like {@link #money} but an empty box means zero. */
    static Money moneyOrZero(TextField f, String what) {
        return f.getText() == null || f.getText().isBlank() ? Money.zero() : money(f, what);
    }

    static Integer year(TextField f, String what) {
        String t = f.getText() == null ? "" : f.getText().trim();
        if (t.isEmpty()) return null;
        try {
            int y = Integer.parseInt(t);
            if (y < 1900 || y > 2100) throw new NumberFormatException();
            return y;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(capital(what) + " must be a year, for example 2004.");
        }
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String message(Throwable e) {
        String msg = e.getMessage();
        return msg == null || msg.isBlank() ? "Something went wrong (" + e.getClass().getSimpleName() + ")." : msg;
    }

    // ------------------------------------------------------------------ tables

    static <T> TableView<T> table(String emptyText) {
        TableView<T> t = new TableView<>();
        t.setPlaceholder(muted(emptyText));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.getStyleClass().addAll(Styles.STRIPED, "data-table");
        return t;
    }

    /** Makes a table exactly as tall as its rows (between 2 and maxRows rows), so there is no empty block. */
    static <T> void fitRows(TableView<T> t, int maxRows) {
        t.setFixedCellSize(40);
        Runnable fit = () -> {
            int n = Math.max(2, Math.min(maxRows, t.getItems().size()));
            double h = n * 40 + 46;
            t.setPrefHeight(h);
            t.setMinHeight(h);
            t.setMaxHeight(h);
        };
        t.getItems().addListener((javafx.collections.ListChangeListener<T>) c -> fit.run());
        fit.run();
    }

    static <T> TableColumn<T, String> column(String title, Function<T, Object> value, double width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(String.valueOf(value.apply(cell.getValue()))));
        c.setPrefWidth(width);
        return c;
    }

    /** A right-aligned money column; positive amounts are shown in green. */
    static <T> TableColumn<T, Money> moneyColumn(String title, Function<T, Money> value, double width, boolean colour) {
        TableColumn<T, Money> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Money m, boolean empty) {
                super.updateItem(m, empty);
                getStyleClass().removeAll("money-in");
                setText(empty || m == null ? null : m.toString());
                if (!empty && m != null && colour && m.isPositive()) getStyleClass().add("money-in");
            }
        });
        c.setStyle("-fx-alignment: CENTER-RIGHT;");
        c.setPrefWidth(width);
        return c;
    }

    /** A column whose cells show a node built from the row (chips, buttons, bars). */
    static <T> TableColumn<T, T> nodeColumn(String title, Function<T, Node> node, double width) {
        TableColumn<T, T> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : node.apply(item));
            }
        });
        c.setPrefWidth(width);
        c.setSortable(false);
        return c;
    }
}
