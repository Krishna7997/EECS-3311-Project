package com.maplecfo.agent;

import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Transaction;
import com.maplecfo.domain.UserProfile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Builds the system prompt and the message list for the agent. */
public class PromptBuilder {

    public String buildSystemPrompt(UserProfile p, LocalDate today) {
        StringBuilder sb = new StringBuilder("""
                You are MapleCFO, a personal-finance assistant for a young person in Canada.
                Today is %s. Amounts are in Canadian dollars.

                Rules:
                1. Never calculate or guess a number yourself. Every dollar amount, date or percentage in your
                   answer must come from a tool result in this conversation. If no tool gives it, say you don't know.
                2. Use the tools: call every tool you need, then answer. Use several tools for questions that need them.
                3. You cannot change the user's data. To change a budget, call propose_action; the user must confirm it.
                4. If a tool returns an error, explain it simply (for example which profile field is missing) and suggest
                   the next step. Do not pretend it worked.
                5. Give education, not advice: never recommend specific stocks, funds or other investments.
                6. Be brief: two to five short sentences, plain language, amounts written like $1,234.56.
                """.formatted(today));
        if (p != null && p.getProvince() != null) {
            sb.append("\nThe user lives in ").append(p.getProvince()).append('.');
        }
        return sb.toString();
    }

    public List<ChatMessage> buildMessages(String systemPrompt, ConversationMemory m, String userMsg) {
        List<ChatMessage> msgs = new ArrayList<>();
        msgs.add(ChatMessage.system(systemPrompt));
        msgs.addAll(m.all());
        msgs.add(ChatMessage.user(userMsg));
        return msgs;
    }

    // ------------------------------------------------------------ F02 categorization

    public String buildCategorizationSystemPrompt() {
        StringBuilder names = new StringBuilder();
        for (Category c : Category.values()) {
            if (c != Category.UNCATEGORIZED) names.append(names.length() > 0 ? ", " : "").append(c.name());
        }
        return "You categorize Canadian bank transactions. Reply with exactly one word from this list and nothing else: "
                + names + ". If you are not sure, reply OTHER.";
    }

    public String buildCategorizationPrompt(Transaction t) {
        return "Description: " + t.getDescription() + "\nAmount: " + t.getAmount()
                + (t.isExpense() ? " (money out)" : " (money in)");
    }

    // ------------------------------------------------------------ F12 monthly summary

    public String buildSummarySystemPrompt() {
        return """
                You write a short monthly money summary for a young person in Canada.
                Use only the figures in the JSON you are given; do not calculate new numbers or add amounts.
                Write 3 to 5 friendly sentences: how the month went, the biggest spending category, any budget
                that went over, and one practical suggestion. No investment advice.""";
    }

    public String buildSummaryPrompt(String reportJson) {
        return "Monthly figures:\n" + reportJson;
    }

    // ------------------------------------------------------------ F16 AI reads an unknown bank CSV

    public String buildFormatSystemPrompt() {
        return """
                You read the first lines of a CSV file exported from a Canadian bank or credit card and describe its
                columns so a program can import it. Reply with only one JSON object, no other text:
                {"hasHeader": true or false,
                 "dateColumn": "...", "dateFormat": "Java DateTimeFormatter pattern, e.g. MM/dd/yyyy or yyyy-MM-dd",
                 "descriptionColumn": "...",
                 "amountColumn": "..." or null,
                 "debitColumn": "..." or null, "creditColumn": "..." or null,
                 "negateAmounts": true or false,
                 "balanceColumn": "..." or null,
                 "explanation": "one short sentence"}
                Name each column by its exact header text. If the first line is data, not a header, set hasHeader to
                false and name columns by their position as a string: "1", "2", ...
                Use amountColumn when one column holds signed amounts. Use debitColumn (money out) and creditColumn
                (money in) when they are separate columns; then amountColumn is null.
                negateAmounts is true only when purchases appear as positive numbers in amountColumn.
                Use the most specific description column (merchant or payee name).""";
    }

    public String buildFormatPrompt(List<String> firstLines) {
        return "First lines of the file:\n" + String.join("\n", firstLines);
    }

    // ------------------------------------------------------------ F18 plain-English transaction search

    public String buildTransactionQuerySystemPrompt(LocalDate today, List<String> accountIds) {
        StringBuilder names = new StringBuilder();
        for (Category c : Category.values()) names.append(names.length() > 0 ? ", " : "").append(c.name());
        return """
                You turn a question about bank transactions into a search filter. Today is %s.
                Reply with only one JSON object, no other text:
                {"from": "YYYY-MM-DD" or null, "to": "YYYY-MM-DD" or null, "account": one of [%s] or null,
                 "category": one of [%s] or null, "direction": "IN", "OUT" or null,
                 "min": number or null, "max": number or null,
                 "text": merchant or words to find in the description, or null}
                "In October" means the whole month of the most recent October up to today. "Over $30" sets min,
                "under $30" sets max. Spending, purchases and payments are OUT; deposits, pay and refunds are IN.
                Use text only for a merchant or payee name (e.g. "uber eats"), never for a category word.
                Leave every field you are not sure about null.""".formatted(today, String.join(", ", accountIds), names);
    }
}
