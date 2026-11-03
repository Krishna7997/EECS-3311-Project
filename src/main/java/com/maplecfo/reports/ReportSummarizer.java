package com.maplecfo.reports;

import com.maplecfo.agent.PromptBuilder;
import com.maplecfo.agent.ResponseValidator;
import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.agent.llm.LlmException;
import com.maplecfo.domain.BudgetStatus;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;

import java.util.List;
import java.util.Map;

/**
 * F12, hybrid: the AI writes the narrative from the computed figures only. If
 * the model is unavailable, in demo mode, or its text contains an amount that
 * is not in the figures, a template narrative is used instead.
 */
public class ReportSummarizer {

    private final LlmClient llm;
    private final PromptBuilder promptBuilder;
    private final ResponseValidator validator;

    public ReportSummarizer(LlmClient llm, PromptBuilder promptBuilder, ResponseValidator validator) {
        this.llm = llm;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
    }

    public MonthlyReport summarize(MonthlyReport r) {
        if (!llm.isMock()) {
            try {
                String json = r.figuresAsJson();
                String text = llm.chat(List.of(ChatMessage.system(promptBuilder.buildSummarySystemPrompt()),
                        ChatMessage.user(promptBuilder.buildSummaryPrompt(json))), List.of()).text().trim();
                if (!text.isBlank() && validator.validate(text, List.of(json)).grounded()) {
                    return r.withNarrative(text, true);
                }
            } catch (LlmException e) {
                // fall back to the template below
            }
        }
        return r.withNarrative(templateSummary(r), false);
    }

    /** A deterministic narrative built only from the report's figures. */
    String templateSummary(MonthlyReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("In ").append(r.month()).append(" you earned ").append(r.income())
                .append(" and spent ").append(r.spending()).append(", ");
        sb.append(r.saved().isNegative() ? "so you spent " + r.saved().abs() + " more than you earned."
                : "so you saved " + r.saved() + " (" + Math.round(r.savingsRatePercent()) + "% of income).");
        r.spendingByCategory().entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e ->
                sb.append(" Your biggest category was ").append(e.getKey()).append(" at ").append(e.getValue()).append('.'));
        Money change = r.spendingChange();
        if (!change.isZero()) {
            sb.append(" That is ").append(change.abs()).append(change.isPositive() ? " more" : " less")
                    .append(" than last month.");
        }
        List<BudgetStatus> over = r.budgets().stream().filter(BudgetStatus::isExceeded).toList();
        if (!over.isEmpty()) {
            sb.append(" Over budget: ");
            sb.append(String.join(", ", over.stream().map(s -> s.budget().category() + " (" + s.spent() + " of "
                    + s.budget().limit() + ")").toList())).append('.');
        }
        if (!r.priceIncreases().isEmpty()) {
            sb.append(" Price increases: ").append(String.join(", ", r.priceIncreases())).append('.');
        }
        Category top = r.spendingByCategory().entrySet().stream().filter(e -> e.getKey() != Category.RENT)
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        if (top != null) {
            sb.append(" Tip: setting a monthly budget for ").append(top).append(" is the easiest place to save.");
        }
        return sb.toString();
    }
}
