package com.maplecfo.ui.gui;

import com.maplecfo.agent.AgentResponse;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.app.MapleCfoFacade;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * F10 "Ask MapleCFO": chat with the AI agent. Questions run on a background thread so the window
 * never freezes; any change the AI suggests needs a click on Confirm before it happens.
 */
final class ChatView implements View {

    static final List<String> SUGGESTIONS = List.of("How much did I spend on dining last month?",
            "Am I on track for my goals?", "Can I afford $1,500 by March?", "What is my TFSA room?",
            "Which subscriptions do I pay for?", "What is my net worth?", "How long until I'm debt free?",
            "Set my dining budget to $200");

    private static volatile boolean asked;

    static boolean hasAsked() {
        return asked;
    }

    private final MapleCfoFacade app;
    private final VBox messages = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final Button send = Ui.primary("Ask", Icons.SPARK);
    private final Label usage = Ui.small("");
    private VBox welcome;
    private final VBox root;

    ChatView(MapleCfoFacade app, Nav nav) {
        this.app = app;
        messages.setPadding(new Insets(4, 4, 12, 4));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("chat-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        input.setPromptText("Ask about your money…");
        input.getStyleClass().add("ask-field");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> ask(input.getText()));
        send.setOnAction(e -> ask(input.getText()));
        Button clear = Ui.secondary("New chat");
        clear.setOnAction(e -> {
            app.clearConversation();
            welcome = welcome();
            messages.getChildren().setAll(welcome);
        });
        HBox bar = new HBox(10, input, send, clear);
        bar.setAlignment(Pos.CENTER);
        bar.getStyleClass().addAll("card", "ask-box");
        bar.setPadding(new Insets(10));

        welcome = welcome();
        messages.getChildren().add(welcome);
        root = Ui.page(scroll, bar, usage);
    }

    @Override public String id() { return Nav.CHAT; }
    @Override public String title() { return "Ask MapleCFO"; }
    @Override public String subtitle() { return "Your AI CFO answers from your own data and asks before changing anything"; }
    @Override public String section() { return ""; }
    @Override public String icon() { return Icons.CHAT; }
    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        LlmClient llm = app.getLlm();
        LlmClient.UsageTotals u = llm.usage();
        usage.setText(llm.isMock()
                ? "Demo mode: answers come from an offline mock model. Add LLM_API_KEY to .env for real AI answers."
                : "Model " + llm.modelName() + " · " + u.requests() + " requests · " + u.inputTokens()
                + " input / " + u.outputTokens() + " output tokens this session. Every amount is checked against your data.");
    }

    private VBox welcome() {
        Label hi = new Label("Hi! What would you like to know?");
        hi.getStyleClass().add(atlantafx.base.theme.Styles.TITLE_3);
        Label sub = Ui.muted("I look up your transactions, budgets, goals, accounts and TFSA/RRSP/FHSA room to answer. "
                + "I never change anything without asking you first. General education, not financial advice.");
        FlowPane chips = new FlowPane(8, 8);
        for (String s : SUGGESTIONS) {
            Button b = new Button(s);
            b.getStyleClass().add("suggestion");
            b.setOnAction(e -> ask(s));
            chips.getChildren().add(b);
        }
        VBox box = new VBox(10, hi, sub, Ui.small("Try one of these:"), chips);
        box.getStyleClass().addAll("card", "chat-welcome");
        box.setPadding(new Insets(22));
        return box;
    }

    /** Package-private so Home (and the smoke test) can ask a question. */
    void ask(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) return;
        asked = true;
        input.clear();
        add(bubble(q, true));
        Label thinking = Ui.muted("MapleCFO is thinking…");
        messages.getChildren().add(thinking);
        send.setDisable(true);
        Task<AgentResponse> task = new Task<>() {
            @Override
            protected AgentResponse call() {
                return app.askCfo(q);
            }
        };
        task.setOnSucceeded(e -> {
            messages.getChildren().remove(thinking);
            showAnswer(task.getValue());
            send.setDisable(false);
            refresh();
        });
        task.setOnFailed(e -> {
            messages.getChildren().remove(thinking);
            add(bubble("Sorry, something went wrong: " + Ui.message(task.getException()), false));
            send.setDisable(false);
        });
        Thread t = new Thread(task, "maplecfo-agent");
        t.setDaemon(true);
        t.start();
    }

    /** Package-private for the smoke test (which runs the agent on the FX thread). */
    void showAnswer(AgentResponse r) {
        asked = true;
        VBox box = bubble(r.answer(), false);
        if (!r.toolsUsed().isEmpty()) {
            box.getChildren().add(Ui.small("Looked up: " + String.join(", ", r.toolsUsed().stream()
                    .map(Ui::pretty).toList()) + (r.grounded() ? "" : "  ·  some numbers could not be verified")));
        }
        String id = r.pendingActionId();
        if (id != null) {
            Button confirm = Ui.primary("Confirm");
            Button dismiss = Ui.secondary("Dismiss");
            Label done = Ui.small("");
            confirm.setOnAction(e -> Ui.attempt(() -> {
                done.setText("Done: " + app.confirmProposedAction(id) + ". You can undo it at the top.");
                confirm.setDisable(true);
                dismiss.setDisable(true);
            }));
            dismiss.setOnAction(e -> Ui.attempt(() -> {
                app.dismissProposedAction(id);
                done.setText("Dismissed. Nothing was changed.");
                confirm.setDisable(true);
                dismiss.setDisable(true);
            }));
            box.getChildren().add(Ui.row(confirm, dismiss, done));
        }
        add(box);
    }

    private VBox bubble(String text, boolean user) {
        Label l = new Label(text);
        l.setWrapText(true);
        VBox b = new VBox(6, l);
        b.getStyleClass().add(user ? "chat-user" : "chat-ai");
        b.setMaxWidth(720);
        return b;
    }

    private void add(VBox bubble) {
        messages.getChildren().remove(welcome);
        HBox line = new HBox(bubble);
        line.setAlignment(bubble.getStyleClass().contains("chat-user") ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messages.getChildren().add(line);
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }
}
