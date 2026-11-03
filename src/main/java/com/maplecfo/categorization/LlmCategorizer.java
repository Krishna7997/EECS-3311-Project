package com.maplecfo.categorization;

import com.maplecfo.agent.PromptBuilder;
import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.CategorySource;
import com.maplecfo.domain.Transaction;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * F02, AI part: asks the LLM for a category when no rule matched. The answer is
 * accepted only if it is exactly one category name; anything else (extra text,
 * an unknown name) leaves the transaction for review (Stage 1 decision D7).
 * In demo mode (mock model) it does nothing.
 */
public class LlmCategorizer implements CategorizationStrategy {

    private final LlmClient llm;
    private final PromptBuilder promptBuilder;
    /** One question per merchant per run, so a file with 20 rows from one store costs one call. */
    private final Map<String, Optional<Category>> cache = new HashMap<>();

    public LlmCategorizer(LlmClient llm, PromptBuilder promptBuilder) {
        this.llm = llm;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public synchronized Optional<Category> categorize(Transaction t) {
        if (llm.isMock()) {
            return Optional.empty();
        }
        String key = Transaction.normalise(t.getMerchant());
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        String reply = llm.chat(List.of(ChatMessage.system(promptBuilder.buildCategorizationSystemPrompt()),
                ChatMessage.user(promptBuilder.buildCategorizationPrompt(t))), List.of()).text();
        Optional<Category> c = parseCategory(reply);
        cache.put(key, c);
        return c;
    }

    /** Accepts "DINING" or "dining" (optionally with a final period); rejects anything else. */
    static Optional<Category> parseCategory(String reply) {
        if (reply == null) return Optional.empty();
        String cleaned = reply.trim().replaceAll("\\.$", "").toUpperCase(Locale.ROOT);
        if (!cleaned.matches("[A-Z_]+")) {
            return Optional.empty();
        }
        Category c = Category.parseOrNull(cleaned);
        return c == null || c == Category.UNCATEGORIZED ? Optional.empty() : Optional.of(c);
    }

    @Override
    public CategorySource source() {
        return CategorySource.AI;
    }
}
