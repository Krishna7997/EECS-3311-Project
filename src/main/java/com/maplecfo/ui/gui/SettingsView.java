package com.maplecfo.ui.gui;

import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.app.AppContext;
import com.maplecfo.app.MapleCfoFacade;
import com.maplecfo.automation.ImportFolderWatcher;
import com.maplecfo.automation.limits.LimitsStatus;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;

/** Settings and status: AI connection, automatic import folder (F13), contribution limits (F15), reminders. */
final class SettingsView implements View {

    private final MapleCfoFacade app;
    private final VBox content = Ui.page();
    private final ScrollPane scroll = Ui.scroll(content);
    private final Label importResult = Ui.small("");
    private final Label limitsResult = Ui.small("");
    private final Label remindersResult = Ui.small("");

    private final Nav nav;

    SettingsView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        this.nav = nav;
    }

    @Override public String id() { return Nav.SETTINGS; }
    @Override public String title() { return "Settings"; }
    @Override public String subtitle() { return "AI connection, automatic import and contribution limits"; }
    @Override public String section() { return ""; }
    @Override public String icon() { return Icons.GEAR; }
    @Override public Node root() { return scroll; }

    @Override
    public void refresh() {
        content.getChildren().clear();
        LlmClient llm = app.getLlm();
        LlmClient.UsageTotals u = llm.usage();
        Node ai = Ui.card("AI assistant", llm.isMock()
                        ? "Demo mode: an offline mock model answers using the same finance tools."
                        : "Connected to " + llm.modelName() + ".", llm.isMock() ? Ui.chip("Demo mode", "warn")
                        : Ui.chip("Connected", "ok"),
                Ui.muted(llm.isMock()
                        ? "To use Claude, copy .env.example to .env in the MapleCFO folder and put your API key in "
                        + "LLM_API_KEY. The key stays on your computer and is never shared or committed."
                        : "This session: " + u.requests() + " requests, " + u.inputTokens() + " input tokens, "
                        + u.outputTokens() + " output tokens."));

        Path inbox = app.getImportFolder();
        Button change = Ui.primary("Change folder…");
        change.setOnAction(e -> {
            javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
            dc.setTitle("Choose the folder where you save bank CSV files");
            if (java.nio.file.Files.isDirectory(inbox)) dc.setInitialDirectory(inbox.toFile());
            java.io.File chosen = dc.showDialog(content.getScene().getWindow());
            if (chosen != null) {
                Ui.attempt(() -> {
                    Path saved = app.setImportFolder(chosen.toPath());
                    nav.importFolderChanged();
                    importResult.setText("Now watching " + saved + ".");
                });
                refresh();
            }
        });
        Button reset = Ui.flat("Use the default folder");
        reset.setOnAction(e -> {
            Ui.attempt(() -> {
                app.setImportFolder(AppContext.defaultImportFolder());
                nav.importFolderChanged();
                importResult.setText("Back to the default folder.");
            });
            refresh();
        });
        Button scan = Ui.secondary("Check the folder now");
        scan.setOnAction(e -> Ui.attempt(() -> {
            List<ImportFolderWatcher.Outcome> done = app.newImportWatcher(inbox, "chequing").scanOnce();
            importResult.setText(done.isEmpty() ? "No new files." : String.join("\n",
                    done.stream().map(ImportFolderWatcher.Outcome::message).toList()));
        }));
        VBox auto = Ui.card("Automatic import", "Save bank CSVs into this folder and MapleCFO imports them while "
                + "it is open. Put the account name in the file name, e.g. visa_nov.csv. Your Downloads folder "
                + "works too.", null, selectable(inbox.toString()), new javafx.scene.layout.FlowPane(10, 8, change, scan, reset), importResult);
        if (app.isImportFolderFromEnvironment()) {
            auto.getChildren().add(Ui.banner("MAPLECFO_INBOX is set on this computer, so that folder is used.", "info"));
        }

        Button refreshLimits = Ui.secondary("Check for new limits");
        refreshLimits.setOnAction(e -> {
            LimitsStatus st = app.refreshLimits(true);
            limitsResult.setText(st.message());
            refresh();
        });
        Node limits = Ui.card("TFSA / RRSP / FHSA limits", "Checked online once a week and validated before use. "
                + "If the check fails, the last good limits are kept.", null,
                Ui.muted(app.describeLimits()), Ui.row(refreshLimits), limitsResult);

        Button remind = Ui.secondary("Check reminders");
        remind.setOnAction(e -> {
            List<String> due = app.checkReminders();
            remindersResult.setText(due.isEmpty() ? "Nothing due right now." : String.join("\n", due));
        });
        Node reminders = Ui.card("Reminders", "The monthly check-in, \"no import in 30 days\" and "
                + "\"monthly summary ready\". Checked when MapleCFO starts.", null, Ui.row(remind), remindersResult);

        Node data = Ui.card("Your data", "Everything is stored only on this computer.", null,
                selectable(AppContext.defaultDatabasePath().toString()));
        content.getChildren().add(new ResponsiveGrid(380, 16, ai, auto, limits, reminders));
        content.getChildren().add(data);
    }

    private static Node selectable(String text) {
        javafx.scene.control.TextField f = new javafx.scene.control.TextField(text);
        f.setEditable(false);
        f.getStyleClass().add("path-field");
        return f;
    }
}
