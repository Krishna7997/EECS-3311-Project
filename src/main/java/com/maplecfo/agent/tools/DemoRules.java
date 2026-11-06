package com.maplecfo.agent.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.maplecfo.agent.llm.MockLlmClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keyword rules the offline mock uses to pick a tool when no API key is set (demo mode). */
public final class DemoRules {

    private static final Pattern AMOUNT = Pattern.compile("\\$?\\s?(\\d[\\d,]*(?:\\.\\d+)?)");

    private DemoRules() {
    }

    public static List<MockLlmClient.Rule> forMapleCfo() {
        List<MockLlmClient.Rule> rules = new ArrayList<>();
        rules.add(new MockLlmClient.Rule(q -> q.contains("afford"), "affordability", DemoRules::affordArgs));
        rules.add(new MockLlmClient.Rule(q -> q.contains("net worth") || q.contains("worth"), "net_worth", q -> MockLlmClient.args()));
        rules.add(new MockLlmClient.Rule(q -> q.contains("goal"), "goal_forecast", q -> MockLlmClient.args()));
        rules.add(new MockLlmClient.Rule(q -> q.matches(".*\\b(tfsa|rrsp|fhsa|room|contribut).*"), "contribution_room", DemoRules::roomArgs));
        rules.add(new MockLlmClient.Rule(q -> q.contains("budget") && q.matches(".*\\b(set|change|make)\\b.*")
                && categoryIn(q) != null && firstAmount(q).isPresent(), "propose_action", DemoRules::budgetProposalArgs));
        rules.add(new MockLlmClient.Rule(q -> q.contains("budget"), "budget_status", DemoRules::monthArgs));
        rules.add(new MockLlmClient.Rule(q -> q.contains("subscription") || q.contains("recurring"), "recurring_charges", q -> MockLlmClient.args()));
        rules.add(new MockLlmClient.Rule(q -> q.contains("debt") || q.contains("loan") || q.contains("pay off"), "debt_payoff", q -> MockLlmClient.args()));
        rules.add(new MockLlmClient.Rule(q -> q.contains("spend") || q.contains("spent"), "spending_summary", DemoRules::monthArgs));
        return rules;
    }

    private static final String[] MONTHS = {"january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december"};

    /** "october 2026", "in october" or "last month" become {"month":"2026-10"}. */
    public static ObjectNode monthArgs(String q) {
        ObjectNode a = MockLlmClient.args();
        java.time.YearMonth now = java.time.YearMonth.now();
        if (q.contains("last month")) {
            a.put("month", now.minusMonths(1).toString());
            return a;
        }
        for (int i = 0; i < MONTHS.length; i++) {
            Matcher m = Pattern.compile("\\b" + MONTHS[i] + "\\b(?:\\s+(\\d{4}))?").matcher(q);
            if (m.find()) {
                int year = m.group(1) != null ? Integer.parseInt(m.group(1))
                        : (i + 1 > now.getMonthValue() ? now.getYear() - 1 : now.getYear());
                a.put("month", java.time.YearMonth.of(year, i + 1).toString());
                return a;
            }
        }
        return a;
    }

    private static ObjectNode roomArgs(String q) {
        ObjectNode a = MockLlmClient.args();
        a.put("account", q.contains("rrsp") ? "RRSP" : q.contains("fhsa") ? "FHSA" : "TFSA");
        firstAmount(q).ifPresent(v -> a.put("amount", v));
        return a;
    }

    /** "set my dining budget to 200" -> {action: set_budget, category: DINING, amount: 200}. */
    private static ObjectNode budgetProposalArgs(String q) {
        ObjectNode a = MockLlmClient.args();
        a.put("action", "set_budget");
        a.put("category", categoryIn(q).name());
        firstAmount(q).ifPresent(v -> a.put("amount", v));
        return a;
    }

    private static com.maplecfo.domain.Category categoryIn(String q) {
        for (com.maplecfo.domain.Category c : com.maplecfo.domain.Category.values()) {
            String word = c.name().toLowerCase();
            if (q.contains(word) || (word.endsWith("s") && q.contains(word.substring(0, word.length() - 1)))) {
                return c;
            }
        }
        if (q.contains("restaurant") || q.contains("eating out")) return com.maplecfo.domain.Category.DINING;
        return null;
    }

    private static ObjectNode affordArgs(String q) {
        ObjectNode a = MockLlmClient.args();
        firstAmount(q).ifPresent(v -> a.put("amount", v));
        affordDate(q, java.time.LocalDate.now()).ifPresent(d -> a.put("date", d.toString()));
        return a;
    }

    /** Understands "2027-06-30", "in 4 months" and "by june" (the next June, last day of the month). */
    public static java.util.Optional<java.time.LocalDate> affordDate(String q, java.time.LocalDate today) {
        Matcher iso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(q);
        if (iso.find()) {
            return java.util.Optional.of(java.time.LocalDate.parse(iso.group(1)));
        }
        Matcher in = Pattern.compile("in (\\d+) months?").matcher(q);
        if (in.find()) {
            return java.util.Optional.of(today.plusMonths(Integer.parseInt(in.group(1))));
        }
        for (int i = 0; i < MONTHS.length; i++) {
            if (Pattern.compile("\\b" + MONTHS[i] + "\\b").matcher(q).find()) {
                int year = i + 1 >= today.getMonthValue() ? today.getYear() : today.getYear() + 1;
                Matcher y = Pattern.compile("\\b" + MONTHS[i] + "\\s+(20\\d{2})\\b").matcher(q);
                if (y.find()) year = Integer.parseInt(y.group(1));
                return java.util.Optional.of(java.time.YearMonth.of(year, i + 1).atEndOfMonth());
            }
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<Double> firstAmount(String q) {
        // ignore dates and "in N months" so they are not read as the amount
        Matcher m = AMOUNT.matcher(q.replaceAll("\\d{4}-\\d{2}-\\d{2}", " ").replaceAll("in \\d+ months?", " ")
                .replaceAll("\\b(" + String.join("|", MONTHS) + ")\\s+20\\d{2}\\b", "$1"));
        while (m.find()) {
            String digits = m.group(1).replace(",", "");
            double v = Double.parseDouble(digits);
            if (v >= 10) return java.util.Optional.of(v); // skip years-like small words? keep amounts >= $10
        }
        return java.util.Optional.empty();
    }
}
