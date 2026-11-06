package com.maplecfo.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maplecfo.agent.llm.ChatMessage;
import com.maplecfo.agent.llm.LlmClient;
import com.maplecfo.domain.Category;
import com.maplecfo.domain.Money;
import com.maplecfo.services.TransactionFilter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F18 (new in Stage 2): "Uber Eats over $30 in October" becomes a {@link TransactionFilter}. The AI only fills in
 * the filter fields; Java checks every field (real dates, known categories and accounts, sensible amounts) and the
 * search itself is the normal deterministic filter. Without an AI model, simple keyword rules are used instead.
 */
public class TransactionQueryParser {

    /** The filter that was understood, and whether the AI or the keyword rules produced it. */
    public record ParsedQuery(TransactionFilter filter, boolean byAi, List<String> ignored) {
        public ParsedQuery {
            ignored = List.copyOf(ignored);
        }

        public String explanation() {
            return "Showing " + filter.describe() + (ignored.isEmpty() ? "" : " (ignored: " + String.join("; ", ignored) + ")");
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] MONTHS = {"january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december"};

    private final LlmClient llm;
    private final PromptBuilder prompts;

    public TransactionQueryParser(LlmClient llm, PromptBuilder prompts) {
        this.llm = llm;
        this.prompts = prompts;
    }

    public ParsedQuery parse(String question, List<String> accountIds, LocalDate today) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Type what you are looking for, e.g. Uber Eats over $30 in October.");
        }
        if (llm.isMock()) {
            return byRules(question, accountIds, today);
        }
        String reply = llm.chat(List.of(ChatMessage.system(prompts.buildTransactionQuerySystemPrompt(today, accountIds)),
                ChatMessage.user(question)), List.of()).text();
        return fromJson(reply, accountIds);
    }

    /** Checks each field the AI filled in; anything invalid is left out and reported instead of guessed. */
    static ParsedQuery fromJson(String reply, List<String> accountIds) {
        int start = reply == null ? -1 : reply.indexOf('{');
        int end = reply == null ? -1 : reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("The AI did not understand the question. Try the filters instead.");
        }
        JsonNode j;
        try {
            j = JSON.readTree(reply.substring(start, end + 1));
        } catch (IOException e) {
            throw new IllegalArgumentException("The AI's answer could not be read. Try the filters instead.");
        }
        List<String> ignored = new java.util.ArrayList<>();
        LocalDate from = date(j, "from", ignored);
        LocalDate to = date(j, "to", ignored);
        if (from != null && to != null && from.isAfter(to)) {
            ignored.add("dates " + from + " to " + to + " are in the wrong order");
            from = null;
            to = null;
        }
        String account = text(j, "account");
        if (account != null && accountIds.stream().noneMatch(account::equalsIgnoreCase)) {
            ignored.add("unknown account " + account);
            account = null;
        }
        Category category = null;
        String cat = text(j, "category");
        if (cat != null) {
            category = Category.parseOrNull(cat);
            if (category == null) ignored.add("unknown category " + cat);
        }
        TransactionFilter.Direction direction = null;
        String dir = text(j, "direction");
        if (dir != null) {
            if (dir.equalsIgnoreCase("IN")) direction = TransactionFilter.Direction.IN;
            else if (dir.equalsIgnoreCase("OUT")) direction = TransactionFilter.Direction.OUT;
            else ignored.add("unknown direction " + dir);
        }
        Money min = amount(j, "min", ignored);
        Money max = amount(j, "max", ignored);
        if (min != null && max != null && min.isGreaterThan(max)) {
            ignored.add("smallest amount is larger than the largest");
            min = null;
            max = null;
        }
        TransactionFilter f = new TransactionFilter(from, to, account, category, direction, min, max, text(j, "text"));
        return new ParsedQuery(f, true, ignored);
    }

    /** Demo mode: months, categories, accounts, money in/out and "over/under $N" by keyword. */
    static ParsedQuery byRules(String question, List<String> accountIds, LocalDate today) {
        String q = question.toLowerCase(Locale.ROOT);
        TransactionFilter f = TransactionFilter.all();
        YearMonth month = null;
        if (q.contains("last month")) month = YearMonth.from(today).minusMonths(1);
        else if (q.contains("this month")) month = YearMonth.from(today);
        else {
            for (int i = 0; i < MONTHS.length && month == null; i++) {
                Matcher m = Pattern.compile("\\b" + MONTHS[i] + "\\b(?:\\s+(20\\d{2}))?").matcher(q);
                if (m.find()) {
                    int year = m.group(1) != null ? Integer.parseInt(m.group(1))
                            : i + 1 > today.getMonthValue() ? today.getYear() - 1 : today.getYear();
                    month = YearMonth.of(year, i + 1);
                }
            }
        }
        if (month != null) f = f.between(month.atDay(1), month.atEndOfMonth());
        for (Category c : Category.values()) {
            String w = c.name().toLowerCase(Locale.ROOT);
            if (c != Category.UNCATEGORIZED && (q.contains(w) || (w.endsWith("s") && q.contains(w.substring(0, w.length() - 1))))) {
                f = f.category(c);
                break;
            }
        }
        for (String id : accountIds) {
            if (Pattern.compile("\\b" + Pattern.quote(id.toLowerCase(Locale.ROOT)) + "\\b").matcher(q).find()) {
                f = f.account(id);
                break;
            }
        }
        if (q.matches(".*\\b(income|deposits?|money in|refunds?)\\b.*")) f = f.direction(TransactionFilter.Direction.IN);
        else if (q.matches(".*\\b(spent|spending|spend|purchases?|money out|paid)\\b.*")) f = f.direction(TransactionFilter.Direction.OUT);
        Money min = amountAfter(q, "(?:over|more than|above|at least)");
        Money max = amountAfter(q, "(?:under|less than|below|at most)");
        f = f.amounts(min, max);
        Matcher quoted = Pattern.compile("\"([^\"]+)\"").matcher(question);
        if (quoted.find()) f = f.text(quoted.group(1));
        return new ParsedQuery(f, false, List.of());
    }

    private static Money amountAfter(String q, String words) {
        Matcher m = Pattern.compile(words + "\\s+\\$?\\s?(\\d[\\d,]*(?:\\.\\d+)?)").matcher(q);
        return m.find() ? Money.of(new BigDecimal(m.group(1).replace(",", ""))) : null;
    }

    private static LocalDate date(JsonNode j, String field, List<String> ignored) {
        String v = text(j, field);
        if (v == null) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            ignored.add("date " + v + " is not a real date");
            return null;
        }
    }

    private static Money amount(JsonNode j, String field, List<String> ignored) {
        JsonNode v = j.get(field);
        if (v == null || v.isNull()) return null;
        if (!v.isNumber() || v.asDouble() < 0) {
            ignored.add(field + " amount " + v.asText());
            return null;
        }
        return Money.of(v.decimalValue());
    }

    private static String text(JsonNode j, String field) {
        JsonNode v = j.get(field);
        return v == null || v.isNull() || v.asText().isBlank() || v.asText().equalsIgnoreCase("null")
                ? null : v.asText().trim();
    }
}
