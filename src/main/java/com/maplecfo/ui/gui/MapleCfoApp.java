package com.maplecfo.ui.gui;

import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.events.EventType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
    private final Label undoLabel = new Label();
    private Button undo;
    private Button redo;

    @Override
    public void start(Stage stage) {
        app = preset != null ? preset : AppContext.create(AppContext.defaultDatabasePath());
        stage.setScene(buildScene());
        stage.setTitle("MapleCFO — your AI personal CFO");
        stage.setWidth(1280);
        stage.setHeight(820);
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.show();
        startAutomation();
        refreshCurrent();
    }

    private com.maplecfo.automation.ImportFolderWatcher watcher;
    private final Label autoImport = new Label();

    /** F13: watch the inbox folder for bank CSVs and raise any reminders that are due. */
    private void startAutomation() {
        try {
            java.nio.file.Path inbox = AppContext.defaultImportFolder();
            watcher = app.newImportWatcher(inbox, "chequing");
            watcher.start();
            autoImport.setText("Auto-import on\n" + inbox.getFileName() + " folder");
        } catch (Exception e) {
            autoImport.setText("Auto-import is off");
        }
        app.checkReminders();
        // F15: check for newer contribution limits in the background (at most once a week)
        Thread limits = new Thread(() -> app.refreshLimits(false), "maplecfo-limits");
        limits.setDaemon(true);
        limits.start();
    }

    @Override
    public void stop() throws Exception {
        if (watcher != null) {
            watcher.close();
        }
    }

    /** Builds the window content (package-private so a smoke test can render it without a Stage). */
    Scene buildScene() {
        if (app == null) {
            app = preset != null ? preset : AppContext.create(AppContext.defaultDatabasePath());
        }
        views.addAll(createViews(app));

        // ---- sidebar navigation
        Label leaf = new Label();
        leaf.setGraphic(Icons.of(Icons.LEAF, 22));
        leaf.getStyleClass().add("brand-icon");
        Label brand = new Label("MapleCFO");
        brand.getStyleClass().add("brand");
        HBox brandRow = new HBox(8, leaf, brand);
        brandRow.getStyleClass().add("brand-row");
        VBox nav = new VBox(4, brandRow);
        nav.getStyleClass().add("sidebar");
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            ToggleButton b = new ToggleButton(v.title(), Icons.of(v.icon(), 18));
            b.setToggleGroup(navGroup);
            b.getStyleClass().add("nav-item");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setUserData(i);
            b.setOnAction(e -> {
                if (!b.isSelected()) b.setSelected(true); // keep one page selected
                show((int) b.getUserData());
            });
            navButtons.add(b);
            nav.getChildren().add(b);
        }
        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        Label mode = new Label(app.getLlm().isMock() ? "AI demo mode" : "AI: " + app.getLlm().modelName());
        mode.getStyleClass().addAll("chip", app.getLlm().isMock() ? "chip-warn" : "chip-ok");
        autoImport.getStyleClass().add("sidebar-note");
        autoImport.setWrapText(true);
        nav.getChildren().addAll(push, mode, autoImport);

        // ---- page header with undo / redo
        pageTitle.getStyleClass().add("page-title");
        pageSubtitle.getStyleClass().add("page-subtitle");
        VBox titles = new VBox(2, pageTitle, pageSubtitle);
        undo = new Button("Undo");
        undo.getStyleClass().add("ghost");
        undo.setOnAction(e -> Ui.attempt(() -> { app.undoLastEdit(); refreshCurrent(); }));
        redo = new Button("Redo");
        redo.getStyleClass().add("ghost");
        redo.setOnAction(e -> Ui.attempt(() -> { app.redoLastEdit(); refreshCurrent(); }));
        undoLabel.getStyleClass().add("undo-hint");
        undoLabel.setMaxWidth(300);
        undoLabel.setMinWidth(0);
        undo.setMinWidth(Region.USE_PREF_SIZE);
        redo.setMinWidth(Region.USE_PREF_SIZE);
        titles.setMinWidth(0);
        pageSubtitle.setMinWidth(0);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox header = new HBox(10, titles, grow, undoLabel, undo, redo);
        header.getStyleClass().add("page-header");

        BorderPane content = new BorderPane(pageHost);
        content.setTop(header);
        content.getStyleClass().add("content");

        BorderPane root = new BorderPane(content);
        root.setLeft(nav);
        root.getStyleClass().add("app-root");

        // Observer: any change in the model refreshes the visible screen.
        for (EventType type : EventType.values()) {
            app.subscribe(type, e -> {
                if (Platform.isFxApplicationThread()) refreshCurrent();
                else Platform.runLater(this::refreshCurrent);
            });
        }

        Scene scene = new Scene(root);
        scene.getStylesheets().add(MapleCfoApp.class.getResource("/gui/maplecfo.css").toExternalForm());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), undo::fire);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN), redo::fire);
        show(0);
        return scene;
    }

    private final ToggleGroup navGroup = new ToggleGroup();
    private final List<ToggleButton> navButtons = new ArrayList<>();
    private final StackPane pageHost = new StackPane();
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();
    private int current = -1;

    private void show(int index) {
        current = index;
        View v = views.get(index);
        navButtons.get(index).setSelected(true);
        pageTitle.setText(v.title());
        pageSubtitle.setText(v.subtitle());
        pageHost.getChildren().setAll(v.root());
        refreshCurrent();
    }

    /** The tabs, in order. */
    static List<View> createViews(MapleCfoFacade app) {
        return List.of(new DashboardView(app), new ChatView(app), new TransactionsView(app), new BudgetsView(app),
                new AccountsGoalsView(app), new PlanningView(app), new ReportView(app));
    }

    void selectTab(int index) {
        show(index);
    }

    int tabCount() {
        return views.size();
    }

    View view(int index) {
        return views.get(index);
    }

    private void refreshCurrent() {
        if (current < 0 || undo == null) return;
        try {
            views.get(current).refresh();
        } catch (RuntimeException e) {
            Ui.error(e);
        }
        String next = app.describeUndo();
        undoLabel.setText(next.equals("nothing to undo") ? "" : "Undo: " + next);
        undo.setDisable(next.equals("nothing to undo"));
    }
}
