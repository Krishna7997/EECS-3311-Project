package com.maplecfo.ui.gui;

import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.events.EventType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * The JavaFX GUI. It talks only to the {@link MapleCfoFacade}, the same entry point the CLI uses,
 * and listens to the EventBus (Observer) so every screen refreshes when data changes.
 */
public class MapleCfoApp extends Application {

    /** Lets a launcher (or a smoke test) hand in a ready facade; otherwise the default database is used. */
    private static MapleCfoFacade preset;

    static void useFacade(MapleCfoFacade facade) {
        preset = facade;
    }

    private MapleCfoFacade app;
    private final List<View> views = new ArrayList<>();
    private TabPane tabs;
    private final Label undoLabel = new Label();
    private Button undo;
    private Button redo;

    @Override
    public void start(Stage stage) {
        app = preset != null ? preset : AppContext.create(AppContext.defaultDatabasePath());
        stage.setScene(buildScene());
        stage.setTitle("MapleCFO — your AI personal CFO");
        stage.setWidth(1150);
        stage.setHeight(780);
        stage.show();
        refreshCurrent();
    }

    /** Builds the window content (package-private so a smoke test can render it without a Stage). */
    Scene buildScene() {
        if (app == null) {
            app = preset != null ? preset : AppContext.create(AppContext.defaultDatabasePath());
        }
        views.addAll(createViews(app));
        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (View v : views) {
            Tab t = new Tab(v.title(), v.root());
            t.setUserData(v);
            tabs.getTabs().add(t);
        }
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshCurrent());

        // Observer: any change in the model refreshes the visible screen.
        for (EventType type : EventType.values()) {
            app.subscribe(type, e -> {
                if (Platform.isFxApplicationThread()) refreshCurrent();
                else Platform.runLater(this::refreshCurrent);
            });
        }

        undo = new Button("Undo");
        undo.setOnAction(e -> Ui.attempt(() -> { app.undoLastEdit(); refreshCurrent(); }));
        redo = new Button("Redo");
        redo.setOnAction(e -> Ui.attempt(() -> { app.redoLastEdit(); refreshCurrent(); }));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Label mode = new Label(app.getLlm().isMock()
                ? "AI: demo mode (no API key)" : "AI: " + app.getLlm().modelName());
        mode.getStyleClass().add("muted");
        Label brand = new Label("MapleCFO");
        brand.getStyleClass().add("brand");
        HBox top = new HBox(8, brand, grow, undoLabel, undo, redo, mode);
        top.getStyleClass().add("toolbar");

        BorderPane root = new BorderPane(tabs);
        root.setTop(top);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(MapleCfoApp.class.getResource("/gui/maplecfo.css").toExternalForm());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), undo::fire);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN), redo::fire);
        return scene;
    }

    /** The tabs, in order. */
    static List<View> createViews(MapleCfoFacade app) {
        return List.of(new DashboardView(app), new TransactionsView(app), new BudgetsView(app),
                new AccountsGoalsView(app));
    }

    void selectTab(int index) {
        tabs.getSelectionModel().select(index);
        refreshCurrent();
    }

    int tabCount() {
        return tabs.getTabs().size();
    }

    View view(int index) {
        return views.get(index);
    }

    private void refreshCurrent() {
        if (tabs == null) return;
        Tab t = tabs.getSelectionModel().getSelectedItem();
        if (t != null) {
            try {
                ((View) t.getUserData()).refresh();
            } catch (RuntimeException e) {
                Ui.error(e);
            }
        }
        String next = app.describeUndo();
        undoLabel.setText(next.equals("nothing to undo") ? "" : "Undo: " + next);
        undo.setDisable(next.equals("nothing to undo"));
    }
}
