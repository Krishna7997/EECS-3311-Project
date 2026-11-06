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
        VBox box = new VBox(12, children);
        box.setPadding(new Insets(16));
        return box;
    }

    static HBox row(javafx.scene.Node... children) {
        HBox box = new HBox(8, children);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return box;
    }

    static VBox card(javafx.scene.Node... children) {
        VBox box = new VBox(6, children);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(12));
        return box;
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
