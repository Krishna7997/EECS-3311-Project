package com.maplecfo.ui.gui;

import com.maplecfo.agent.AgentResponse;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.app.MapleCfoFacade;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * F10 "Ask MapleCFO": chat with the AI agent. Questions run on a background thread so the window
 * never freezes; any change the AI suggests needs a click on Confirm before it happens.
 */
final class ChatView implements View {

    static final List<String> SUGGESTIONS = List.of("How much did I spend on dining last month?",
            "Am I on track for my goals?", "Can I afford $1,500 by March?", "What is my TFSA room?",
            "Which subscriptions do I pay for?", "What is my net worth?");

    private final MapleCfoFacade app;
    private final VBox messages = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final Button send = new Button("Ask");
    private final Label usage = Ui.muted("");
    private final VBox root;

    ChatView(MapleCfoFacade app) {
        this.app = app;
        messages.setFillWidth(true);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        input.setPromptText("Ask about your money, e.g. \"Can I afford a $1,200 laptop by December?\"");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> ask(input.getText()));
        send.setOnAction(e -> ask(input.getText()));
        Button clear = new Button("New conversation");
        clear.setOnAction(e -> {
            app.clearConversation();
            messages.getChildren().clear();
            greet();
        });

        HBox chips = new HBox(6);
        for (String s : SUGGESTIONS) {
            Button b = new Button(s);
            b.setOnAction(e -> ask(s));
            chips.getChildren().add(b);
        }
        ScrollPane chipScroll = new ScrollPane(chips);
        chipScroll.setFitToHeight(true);
        chipScroll.setPrefHeight(44);
        chipScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        HBox bar = new HBox(8, input, send, clear);
        root = Ui.page(scroll, chipScroll, bar, usage);
        greet();
    }

    @Override public String title() { return "Ask MapleCFO"; }

    @Override public Node root() { return root; }

    @Override
    public void refresh() {
        LlmClient llm = app.getLlm();
        LlmClient.UsageTotals u = llm.usage();
        usage.setText(llm.isMock()
                ? "Demo mode: answers come from an offline mock model. Add LLM_API_KEY to .env for real AI answers."
                : "Model " + llm.modelName() + " · " + u.requests() + " requests · " + u.inputTokens()
                + " input / " + u.outputTokens() + " output tokens this session. Answers are checked against your data.");
    }

    private void greet() {
        add(bubble("Hi! I'm MapleCFO. I answer using your own transactions, budgets, goals and accounts. "
                + "I never change anything without asking you first. (General education, not financial advice.)", false));
    }

    /** Package-private for the smoke test. */
    void ask(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) return;
        input.clear();
        add(bubble(q, true));
        Label thinking = Ui.muted("Thinking…");
        add(thinking);
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
            add(bubble("Sorry, something went wrong: " + task.getException().getMessage(), false));
            send.setDisable(false);
        });
        Thread t = new Thread(task, "maplecfo-agent");
        t.setDaemon(true);
        t.start();
    }

    /** Package-private for the smoke test (which runs the agent on the FX thread). */
    void showAnswer(AgentResponse r) {
        VBox box = bubble(r.answer(), false);
        if (!r.toolsUsed().isEmpty()) {
            box.getChildren().add(Ui.muted("Looked up: " + String.join(", ", r.toolsUsed())
                    + (r.grounded() ? "" : "  ·  some numbers could not be verified")));
        }
        String id = r.pendingActionId();
        if (id != null) {
            Button confirm = new Button("Confirm");
            Button dismiss = new Button("Dismiss");
            Label done = Ui.muted("");
            Consumer<Boolean> finish = ok -> Ui.attempt(() -> {
                if (ok) {
                    done.setText("Done: " + app.confirmProposedAction(id) + " (you can Undo it).");
                } else {
                    app.dismissProposedAction(id);
                    done.setText("Dismissed. Nothing was changed.");
                }
                confirm.setDisable(true);
                dismiss.setDisable(true);
            });
            confirm.setOnAction(e -> finish.accept(true));
            dismiss.setOnAction(e -> finish.accept(false));
            box.getChildren().add(Ui.row(confirm, dismiss, done));
        }
        add(box);
    }

    private VBox bubble(String text, boolean user) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(760);
        VBox b = new VBox(6, l);
        b.getStyleClass().add(user ? "chat-user" : "chat-ai");
        b.setMaxWidth(800);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    private void add(Node n) {
        HBox line = new HBox(n);
        line.setAlignment(n.getStyleClass().contains("chat-user") ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        line.setUserData(n);
        messages.getChildren().add(n instanceof Label ? n : line);
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }
}
