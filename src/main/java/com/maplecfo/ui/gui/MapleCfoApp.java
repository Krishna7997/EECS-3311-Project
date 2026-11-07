package com.maplecfo.ui.gui;

import atlantafx.base.theme.PrimerLight;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.automation.ImportFolderWatcher;
import com.maplecfo.events.EventType;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JavaFX GUI. It talks only to the {@link MapleCfoFacade}, the same entry point the CLI uses,
 * and listens to the EventBus (Observer) so the visible page refreshes when data changes.
 */
public class MapleCfoApp extends Application implements Nav {

    /** Lets a launcher (or a smoke test) hand in a ready facade; otherwise the default database is used. */
    private static MapleCfoFacade preset;

    static void useFacade(MapleCfoFacade facade) {
        preset = facade;
    }

    static String stylesheet() {
        return MapleCfoApp.class.getResource("/gui/maplecfo.css").toExternalForm();
    }

    private MapleCfoFacade app;
    private final List<View> views = new ArrayList<>();
    private final Map<String, ToggleButton> navButtons = new LinkedHashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final StackPane pageHost = new StackPane();
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();
    private final Label undoLabel = new Label();
    private final Label autoImport = new Label();
    private Button undo;
    private Button redo;
    private View current;
    private ImportFolderWatcher watcher;

    @Override
    public void start(Stage stage) {
        app = preset != null ? preset : AppContext.create(AppContext.defaultDatabasePath());
        stage.setScene(buildScene());
        stage.setTitle("MapleCFO — your AI personal CFO");
        setAppIcons(stage);
        stage.setWidth(1320);
        stage.setHeight(860);
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.show();
        startAutomation();
        refreshCurrent();
    }

    /** The MapleCFO icon for the window, the taskbar and (on macOS) the Dock. A missing icon never stops the app. */
    static void setAppIcons(Stage stage) {
        for (int size : new int[] {16, 32, 64, 128, 256, 512}) {
            var in = MapleCfoApp.class.getResourceAsStream("/gui/icons/maplecfo-" + size + ".png");
            if (in != null) stage.getIcons().add(new javafx.scene.image.Image(in));
        }
        try {
            if (java.awt.Taskbar.isTaskbarSupported()
                    && java.awt.Taskbar.getTaskbar().isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                var url = MapleCfoApp.class.getResource("/gui/icons/maplecfo-512.png");
                if (url != null) java.awt.Taskbar.getTaskbar().setIconImage(java.awt.Toolkit.getDefaultToolkit().getImage(url));
            }
        } catch (RuntimeException | Error e) {
            // no desktop taskbar (e.g. a headless test machine): the window icon is enough
        }
    }

    /** The logo shown in the sidebar, or the old leaf shape if the image is missing. */
    static javafx.scene.Node brandLogo(double size) {
        var in = MapleCfoApp.class.getResourceAsStream("/gui/icons/maplecfo-mark-128.png");
        if (in == null) {
            Region leaf = Icons.of(Icons.LEAF, size);
            leaf.getStyleClass().add("brand-icon");
            return leaf;
        }
        javafx.scene.image.ImageView v = new javafx.scene.image.ImageView(new javafx.scene.image.Image(in));
        v.setFitWidth(size);
        v.setFitHeight(size);
        v.setSmooth(true);
        return v;
    }

    /** F13: watch the inbox folder for bank CSVs; raise reminders; F15: check limits in the background. */
    private void startAutomation() {
        startWatcher();
        app.checkReminders();
        Thread limits = new Thread(() -> app.refreshLimits(false), "maplecfo-limits");
        limits.setDaemon(true);
        limits.start();
    }

    private void startWatcher() {
        try {
            if (watcher != null) watcher.close();
            Path inbox = app.getImportFolder();
            watcher = app.newImportWatcher(inbox, "chequing");
            watcher.start();
            autoImport.setText("Auto-import: " + inbox.getFileName());
        } catch (Exception e) {
            autoImport.setText("Auto-import is off");
        }
    }

    @Override
    public void importFolderChanged() {
        startWatcher();
    }

    @Override
    public void stop() throws Exception {
        if (watcher != null) {
            watcher.close();
        }
    }

    /** Builds the window content (package-private so a smoke test can render it without start()). */
    Scene buildScene() {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        if (app == null) {
            app = preset != null ? preset : AppContext.create(AppContext.defaultDatabasePath());
        }
        views.addAll(createViews(app, this));

        // ---- sidebar: brand, grouped pages, status at the bottom
        javafx.scene.Node leaf = brandLogo(42);
        Label brand = new Label("MapleCFO");
        brand.getStyleClass().add("brand");
        HBox brandRow = new HBox(10, leaf, brand);
        brandRow.getStyleClass().add("brand-row");
        brandRow.setAlignment(Pos.CENTER_LEFT);
        VBox nav = new VBox(2, brandRow);
        String section = null;
        for (View v : views) {
            if (!v.section().equals(section)) {
                section = v.section();
                if (!section.isEmpty()) {
                    Label s = new Label(section.toUpperCase());
                    s.getStyleClass().add("nav-section");
                    nav.getChildren().add(s);
                }
            }
            ToggleButton b = new ToggleButton(v.title(), Icons.of(v.icon(), 17));
            b.setToggleGroup(navGroup);
            b.getStyleClass().add("nav-item");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> go(v.id()));
            navButtons.put(v.id(), b);
            nav.getChildren().add(b);
        }
        Region push = new Region();
        VBox.setVgrow(push, Priority.ALWAYS);
        Label mode = new Label(app.getLlm().isMock() ? "AI demo mode" : "AI connected");
        mode.getStyleClass().addAll("chip", app.getLlm().isMock() ? "chip-warn" : "chip-ok");
        autoImport.getStyleClass().add("sidebar-note");
        VBox status = new VBox(6, mode, autoImport);
        status.getStyleClass().add("sidebar-status");
        nav.getChildren().addAll(push, status);
        nav.getStyleClass().add("sidebar");
        ScrollPane navScroll = new ScrollPane(nav);
        navScroll.setFitToWidth(true);
        navScroll.setFitToHeight(true);
        navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navScroll.getStyleClass().add("sidebar-scroll");

        // ---- page header with undo / redo
        pageTitle.getStyleClass().add("page-title");
        pageSubtitle.getStyleClass().add("page-subtitle");
        pageSubtitle.setMinWidth(0);
        VBox titles = new VBox(2, pageTitle, pageSubtitle);
        titles.setMinWidth(0);
        HBox.setHgrow(titles, Priority.ALWAYS);
        undo = Ui.secondary("Undo");
        undo.setOnAction(e -> Ui.attempt(() -> { app.undoLastEdit(); refreshCurrent(); }));
        redo = Ui.secondary("Redo");
        redo.setOnAction(e -> Ui.attempt(() -> { app.redoLastEdit(); refreshCurrent(); }));
        undoLabel.getStyleClass().add("undo-hint");
        undoLabel.setMaxWidth(280);
        undoLabel.setMinWidth(0);
        HBox header = new HBox(10, titles, undoLabel, undo, redo);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-header");

        BorderPane content = new BorderPane(pageHost);
        content.setTop(header);
        content.getStyleClass().add("content");

        BorderPane root = new BorderPane(content);
        root.setLeft(navScroll);
        root.getStyleClass().add("app-root");

        // Observer: any change in the model refreshes the visible page.
        for (EventType type : EventType.values()) {
            app.subscribe(type, e -> {
                if (Platform.isFxApplicationThread()) refreshCurrent();
                else Platform.runLater(this::refreshCurrent);
            });
        }

        // pop-ups (forms, confirmations) open inside the window, on top of the page
        atlantafx.base.controls.ModalPane modal = new atlantafx.base.controls.ModalPane();
        modal.setPersistent(true);
        Ui.useModalPane(modal);
        StackPane layers = new StackPane(root, modal);
        Scene scene = new Scene(layers);
        scene.getStylesheets().add(stylesheet());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), undo::fire);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN), redo::fire);
        go(HOME);
        return scene;
    }

    /** The pages, in sidebar order. */
    static List<View> createViews(MapleCfoFacade app, Nav nav) {
        return List.of(
                new HomeView(app, nav), new ChatView(app, nav),
                new TransactionsView(app, nav), new BudgetsView(app, nav), new SubscriptionsView(app, nav),
                new AccountsView(app, nav),
                new GoalsView(app, nav), new AffordView(app, nav), new RegisteredView(app, nav),
                new DebtView(app, nav), new RetirementView(app, nav),
                new ReportView(app, nav), new SettingsView(app, nav));
    }

    @Override
    public void go(String pageId) {
        View v = views.stream().filter(x -> x.id().equals(pageId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No page " + pageId));
        current = v;
        navButtons.get(pageId).setSelected(true);
        pageTitle.setText(v.title());
        pageSubtitle.setText(v.subtitle());
        pageHost.getChildren().setAll(v.root());
        refreshCurrent();
    }

    @Override
    public void ask(String question) {
        go(CHAT);
        ((ChatView) current).ask(question);
    }

    void selectTab(int index) {
        go(views.get(index).id());
    }

    int tabCount() {
        return views.size();
    }

    View view(int index) {
        return views.get(index);
    }

    private void refreshCurrent() {
        if (current == null || undo == null) return;
        try {
            current.refresh();
        } catch (RuntimeException e) {
            Ui.error(e);
        }
        String next = app.describeUndo();
        boolean none = next.equals("nothing to undo");
        undoLabel.setText(none ? "" : "Last change: " + next);
        undo.setDisable(none);
        redo.setDisable(!app.canRedo());
    }
}
