package com.maplecfo.ui.gui;

import com.maplecfo.domain.Money;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.function.Function;

/** Small helpers shared by the views, so every screen looks and behaves the same way. */
final class Ui {

    private Ui() {
    }

    static Label heading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("heading");
        return l;
    }

    static Label big(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("big-number");
        return l;
    }

    static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    static VBox page(javafx.scene.Node... children) {
        VBox box = new VBox(16, children);
        box.setPadding(new Insets(4, 24, 24, 24));
        box.getStyleClass().add("page");
        return box;
    }

    static HBox row(javafx.scene.Node... children) {
        HBox box = new HBox(8, children);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return box;
    }

    static VBox card(javafx.scene.Node... children) {
        VBox box = new VBox(10, children);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(18));
        return box;
    }

    /** "CREDIT_CARD" -> "Credit card": enum names in a friendlier form for the screen. */
    static String pretty(Object value) {
        String s = String.valueOf(value).replace('_', ' ').toLowerCase();
        if (s.equals("tfsa") || s.equals("rrsp") || s.equals("fhsa")) return s.toUpperCase();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A table column that shows a coloured chip, e.g. a goal's status. */
    static <T> javafx.scene.control.TableColumn<T, T> chipColumn(String title,
                                                              java.util.function.Function<T, Label> chip, double width) {
        javafx.scene.control.TableColumn<T, T> c = new javafx.scene.control.TableColumn<>(title);
        c.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue()));
        c.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : chip.apply(item));
                setText(null);
            }
        });
        c.setPrefWidth(width);
        return c;
    }

    /** A filled, accent-coloured button for the main action on a card. */
    static javafx.scene.control.Button primary(String text) {
        javafx.scene.control.Button b = new javafx.scene.control.Button(text);
        b.getStyleClass().add("primary");
        b.setDefaultButton(false);
        return b;
    }

    /** A small rounded status label: kind is ok, warn, bad or info. */
    static Label chip(String text, String kind) {
        Label l = new Label(text);
        l.getStyleClass().addAll("chip", "chip-" + kind);
        return l;
    }

    /** A KPI tile: small caption, big value, optional note underneath. */
    static VBox stat(String caption, Label value, Label note) {
        Label c = new Label(caption.toUpperCase());
        c.getStyleClass().add("stat-caption");
        value.getStyleClass().setAll("label", "stat-value");
        VBox box = note == null ? new VBox(4, c, value) : new VBox(4, c, value, note);
        box.getStyleClass().addAll("card", "stat");
        box.setPadding(new Insets(16));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** A labelled group of form controls laid out in a wrapping row. */
    static javafx.scene.layout.FlowPane form(javafx.scene.Node... children) {
        javafx.scene.layout.FlowPane f = new javafx.scene.layout.FlowPane(8, 8, children);
        f.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        f.getStyleClass().add("form");
        return f;
    }

    static TextField field(String prompt, double width) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setPrefWidth(width);
        return f;
    }

    static <T> TableColumn<T, String> column(String title, Function<T, Object> value, double width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(String.valueOf(value.apply(cell.getValue()))));
        c.setPrefWidth(width);
        return c;
    }

    static <T> TableView<T> table(String emptyText) {
        TableView<T> t = new TableView<>();
        t.setPlaceholder(new Label(emptyText));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return t;
    }

    /** Makes an id like "td-chequing" from a name, adding -2, -3... if it is already used. */
    static String uniqueId(String name, java.util.Collection<String> taken) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "item";
        String id = base;
        for (int i = 2; taken.contains(id); i++) id = base + "-" + i;
        return id;
    }

    /** Reads a dollar amount such as "1,250.50" or "$80". */
    static Money money(TextField f, String what) {
        String text = f.getText() == null ? "" : f.getText().trim().replace("$", "").replace(",", "");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Enter " + what);
        }
        try {
            return Money.of(new BigDecimal(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number, e.g. 250 or 1,250.50");
        }
    }

    static void error(Throwable e) {
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        Alert a = new Alert(Alert.AlertType.WARNING, msg);
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

    /** Runs an action and shows any validation error in a dialog instead of crashing. */
    static void attempt(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            error(e);
        }
    }
}
