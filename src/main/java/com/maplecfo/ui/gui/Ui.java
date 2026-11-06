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

    /**
     * Shows a small dialog with a form. The OK button runs the action; if the action fails (for example
     * a missing amount) the message is shown in the dialog and it stays open so the user can fix it.
     *
     * @return true when the action ran successfully
     */
    static boolean dialog(String title, String intro, Form form, String okText, Action action) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(null);
        Label heading = new Label(title);
        heading.getStyleClass().add(Styles.TITLE_3);
        Label error = new Label();
        error.getStyleClass().addAll("form-error");
        error.setWrapText(true);
        error.setManaged(false);
        error.setVisible(false);
        VBox content = new VBox(12, heading);
        if (intro != null && !intro.isBlank()) content.getChildren().add(muted(intro));
        content.getChildren().addAll(form.grid, error);
        content.setPadding(new Insets(8, 4, 0, 4));
        content.setPrefWidth(440);
        d.getDialogPane().setContent(content);
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        d.getDialogPane().getStylesheets().add(MapleCfoApp.stylesheet());
        Button okButton = (Button) d.getDialogPane().lookupButton(ok);
        okButton.getStyleClass().add(Styles.ACCENT);
        okButton.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                action.run();
            } catch (Exception ex) {
                error.setText(message(ex));
                error.setManaged(true);
                error.setVisible(true);
                ev.consume();
                d.getDialogPane().getScene().getWindow().sizeToScene();
            }
        });
        return d.showAndWait().filter(b -> b == ok).isPresent();
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

    static void error(Throwable e) {
        Alert a = new Alert(Alert.AlertType.WARNING, message(e));
        a.setHeaderText(null);
        a.setTitle("MapleCFO");
        a.showAndWait();
    }

    static void info(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setHeaderText(null);
        a.setTitle("MapleCFO");
        a.showAndWait();
    }

    static boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        a.setTitle("MapleCFO");
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    /** Runs an action and shows any error in a dialog instead of crashing. */
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
